/** Host-side slide generator.
 *
 * Required classpath entries:
 *  /usr/share/java/bcprov.jar
 *  /usr/share/java/commons-codec.jar
 *  /usr/share/java/commons-cli.jar
 *  $HOME/src/zxing/core/core.jar 
 *
 * Build with:
 *      javac -d bin/classes -cp bin/classes:... \
 *            host/org/ietfng/ns/android/vcpass/VCSlideGen.java
 *
 */

package org.ietfng.ns.android.vcpass;

import java.io.FileWriter;
import java.security.SecureRandom;
import java.security.Provider;
import java.security.Security;
import javax.crypto.SecretKey;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.ByteMatrix;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.codec.binary.Base64;

public final class VCSlideGen {
    static final private int SEED_SIZE = 16;

    private static final int QR_SIZEX = 400;
    private static final int QR_SIZEY = 400;

    static final private Options cliopts = new Options();
    static final private String OPT_SS_HELP   = "h";
    static final private String OPT_SS_QRFILE = "b";
    static final private String OPT_SS_SECRET = "S";
    static final private String OPT_SS_SLIDEF = "s";
    static final private String OPT_SS_VOCABS = "v";

    private static final int crpix = VCParameters.DISP_X
                                   / VCParameters.GRID_X;
    private static final int ccpix = VCParameters.DISP_Y
                                   / VCParameters.GRID_Y;

    private static final void
    printPBMHeader(FileWriter f, int v, int x, int y) 
    throws java.io.IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("P");
        sb.append(v);
        sb.append(" ");
        sb.append(x);
        sb.append(" ");
        sb.append(y);
        sb.append("\n");
        f.write(sb.toString(), 0, sb.length());
    }

    static {
        cliopts.addOption(OPT_SS_HELP, "help", false, "Show this help");
        cliopts.addOption(OPT_SS_QRFILE, "qrfile", true,
                            "Barcode PNM file basename");
        cliopts.addOption(OPT_SS_SECRET, "secretseed", true,
                            "Secret seed (for development)");
        cliopts.addOption(OPT_SS_SLIDEF, "slidefile", true,
                            "Slide PNM file basename");
        cliopts.addOption(OPT_SS_VOCABS, "vocabfile", true,
                            "Vocabulary PNM file basename");
    }

    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(cliopts, args);

        if(cmd.hasOption(OPT_SS_HELP)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "VCSlideGen" , cliopts );
            return;
        } 

        Security.addProvider(VCParameters.CSPROV);

/* XXX */
        char[] useed = "foo".toCharArray();
        char[] vseed = "bar".toCharArray();

/* XXX
        byte[] useed = new byte[SEED_SIZE];
        byte[] vseed = new byte[SEED_SIZE];

        {        
            SecureRandom seedsr = SecureRandom.getInstance(
                                        VCParameters.CSPRNG
                                  );
            if(cmd.hasOption(OPT_SS_SECRET)) {
                System.err.println("WARN: Using given seed.");
                seedsr.setSeed(cmd.getOptionValue(OPT_SS_SECRET)
                                  .getBytes("UTF-8"));
            } else {
                // seedsr will initialize from the system's RNG when
                // we first pull some data out of it.
            }
            seedsr.nextBytes(useed);
            seedsr.nextBytes(vseed);
        }
*/

        if(cmd.hasOption(OPT_SS_QRFILE)) {
            String encodedseeds = null;
            {
                StringBuilder esb = new StringBuilder();
                Base64 b64 = new Base64(80,new byte[0]);
                // XXX esb.append(b64.encodeToString(useed));
                esb.append(" ");
                // XXX esb.append(b64.encodeToString(vseed));
                encodedseeds = esb.toString();
    	        System.out.printf("QRSTR: %s\n", encodedseeds);
            }
            FileWriter qrout    = new FileWriter(
                                     cmd.getOptionValue(OPT_SS_QRFILE)
                                    +".pbm"
                                  );
            printPBMHeader(qrout, 1, QR_SIZEX, QR_SIZEY);
            ByteMatrix qrbm = new MultiFormatWriter().encode(
                encodedseeds,
                BarcodeFormat.QR_CODE,
                QR_SIZEX, QR_SIZEY);
            byte[][] qr = qrbm.getArray();
            for(int x = 0; x < qr.length; x++) {
                for(int y = 0; y < qr[x].length; y++) {
                    qrout.write('1' - (qr[x][y] & 0x1));
                }
                qrout.write('\n');
            }
            qrout.flush();
            qrout.close();
        }

        if(cmd.hasOption(OPT_SS_SLIDEF)) {
            FileWriter sout    = new FileWriter(
                                    cmd.getOptionValue(OPT_SS_SLIDEF)
                                   +".pbm"
                                 );
            printPBMHeader(sout, 1, VCParameters.DISP_X,
                                    VCParameters.DISP_Y);

            Integer[][] slide = VCGenerator.generateSlide(useed, null);

            int[][] pixels = VCGenerator.vcArrayToPixels(slide);
            for(int r = 0; r < pixels.length; r++) {
                for(int c = 0; c < pixels[r].length; c++) {
                    sout.write(pixels[r][c] == VCParameters.white ?
                                '0' : '1');
                }
                sout.write('\n');
            }
            sout.flush();
            sout.close();
        }

        if(cmd.hasOption(OPT_SS_VOCABS)) {
            int[] plain = new int[VCParameters.GRID_X
                                 *VCParameters.GRID_Y];
            for(int i = 0; i < VCParameters.VCVOC_SIZE; i++) {
                FileWriter vout    = new FileWriter(
                                     cmd.getOptionValue(OPT_SS_VOCABS)
                                     +"-"
                                     +Integer.toString(i)
                                     +".pbm"
                                   );
                printPBMHeader(vout, 1, VCParameters.DISP_X,
                                        VCParameters.DISP_Y);

                for(int j = 0; j < plain.length; j++) {
                    plain[j] = i;
                }

                Integer[][] vocab = VCGenerator.generateChallenge(
                                        vseed, useed, plain, null
                                    );
                int[][] pixels = VCGenerator.vcArrayToPixels(vocab);
                for(int r = 0; r < pixels.length; r++) {
                    for(int c = 0; c < pixels[r].length; c++) {
                        vout.write(pixels[r][c] == VCParameters.white ?
                                    '1' : '0');
                    }
                    vout.write('\n');
                }
                vout.flush();
                vout.close();
            }
        }
    }
}
