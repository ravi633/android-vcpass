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

import org.ietfng.ns.android.vcpass.Utils;

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
		/* Not quite bytes; each uniformly in the range (32,127) */
    static final private int SEED_SIZE = 128;

    private static final int QR_SIZEX = 400;
    private static final int QR_SIZEY = 400;

    static final private Options cliopts = new Options();
    static final private String OPT_SS_HELP   = "h";
    static final private String OPT_SS_QRFILE = "b";
    static final private String OPT_SS_PRNGSD = "R";
    static final private String OPT_SS_SECRET = "S";
    static final private String OPT_SS_VOCSEC = "V";
    static final private String OPT_SS_SLIDEF = "s";
    static final private String OPT_SS_VOCABS = "v";
    static final private String OPT_SS_GENEXV = "x";
    static final private String OPT_SS_GENEXC = "C";

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
        cliopts.addOption(OPT_SS_SLIDEF, "slidefile", true,
                            "Slide PNM file basename");
        cliopts.addOption(OPT_SS_GENEXV, "examplefile", true,
                            "Example slide^vocabulary file basename");
        cliopts.addOption(OPT_SS_PRNGSD, "secretseed", true,
                            "Random seed (devel)");
        cliopts.addOption(OPT_SS_SECRET, "secretseed", true,
                            "Secret seed (devel)");
        cliopts.addOption(OPT_SS_VOCSEC, "secretseed", true,
                            "Vocabulary seed (devel)");
        cliopts.addOption(OPT_SS_VOCABS, "vocabfile", true,
                            "Vocabulary PNM file basename (devel)");
        cliopts.addOption(OPT_SS_GENEXC, "chalfile", true,
                            "Example solved challenge file basename (devel)");
    }

	private static char[] randChars(SecureRandom sr, int size) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < size; i++) {
			/* Man, this is dumb.  Either char should be big enough to
			 * hold any Unicode codepoint or we should give up and use
			 * UTF-8.  But no, we're using UTF-16, because way back in
			 * the day somebody decided upon UCS2 and now we're stuck
			 * with 16 bits forever and ever.
			 *
			 * Moreover, we'd love to use all codepoints, but that
			 * causes our QR coder to flip and kill people.  So we
			 * generate only from lower ASCII omitting C0 and DEL and
			 * hope for the best.
			 */
			sb.appendCodePoint(sr.nextInt(127-32)+32);
		}
		return sb.toString().toCharArray();
	}

    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(cliopts, args);

        if(cmd.hasOption(OPT_SS_HELP) || args.length == 0) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "VCSlideGen" , cliopts );
            return;
        } 

        Security.addProvider(VCParameters.CSPROV);

        char[] useed = null;
        if(cmd.hasOption(OPT_SS_SECRET)) {
            useed = cmd.getOptionValue(OPT_SS_SECRET).toCharArray();
        }

        char[] vseed = null;
        if(cmd.hasOption(OPT_SS_VOCSEC)) {
            vseed = cmd.getOptionValue(OPT_SS_VOCSEC).toCharArray();
        }

		if(useed == null || vseed == null)
        {        
            SecureRandom sr = new SecureRandom();
            if(cmd.hasOption(OPT_SS_PRNGSD)) {
                System.err.println("WARN: Using given seed.");
                sr.setSeed(cmd.getOptionValue(OPT_SS_PRNGSD)
                              .getBytes("UTF-8"));
            } else {
                // sr will initialize from the system's RNG when
                // we first pull some data out of it.
            }
			if(useed == null) useed = randChars(sr, SEED_SIZE);
			if(vseed == null) vseed = randChars(sr, SEED_SIZE);
        }

        if(cmd.hasOption(OPT_SS_QRFILE)) {
            String encodedseeds = Utils.encode_seeds(useed, vseed);
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

        if(cmd.hasOption(OPT_SS_GENEXV)) {
    		int cells = VCParameters.GRID_X
                                   * VCParameters.GRID_Y;
            Integer[][] vocab = new Integer[cells][];
            Integer[][] vslide = new Integer[cells][];

            {
                Integer[][] slide = VCGenerator.generateSlide( useed, null );
                for(int i = 0; i < vslide.length; i++) {
                    vslide[i] = slide[0];
                }
            }

            for(int i = 0; i < vocab.length; i++) {
                int[] plain = new int[VCParameters.GRID_X
                                     *VCParameters.GRID_Y];
                plain[0] = i;

                Integer[][] vocabi = VCGenerator.generateChallenge(
                                        vseed, useed, plain, null
                                    );
           
                vocab[i] = vocabi[0];
            }

            FileWriter vout    = new FileWriter(
                                 cmd.getOptionValue(OPT_SS_GENEXV)
								 + ".pbm"
                               );
            printPBMHeader(vout, 1, VCParameters.DISP_X,
                                    VCParameters.DISP_Y);

            int[][] spixels = VCGenerator.vcArrayToPixels(vslide);
            int[][] vpixels = VCGenerator.vcArrayToPixels(vocab);
  
            for(int i = crpix-1; i < VCParameters.DISP_X-1; i += crpix){
            for(int r = 0; r < vpixels.length; r++) {
                vpixels[i][r] = VCParameters.black;
                vpixels[i+1][r] = VCParameters.black;
                vpixels[r][i] = VCParameters.black;
                vpixels[r][i+1] = VCParameters.black;
            }}
 
            for(int r = 0; r < vpixels.length; r++) {
                for(int c = 0; c < vpixels[r].length; c++) {
                    if(spixels[r][c] == VCParameters.black) {
                         vout.write('1');
                    } else {
                         vout.write(vpixels[r][c] == VCParameters.white ?
                                '0' : '1');
                    }
                }
                vout.write('\n');
            }
            vout.flush();
            vout.close();
        }

        if(cmd.hasOption(OPT_SS_GENEXC)) {
            int[] plain = new int[VCParameters.GRID_X
                                 *VCParameters.GRID_Y];
            for(int i = 0; i < plain.length; i++ ){
                plain[i] = (i*7+8) % VCParameters.VCVOC_SIZE;
            }
            FileWriter vout    = new FileWriter(
                                 cmd.getOptionValue(OPT_SS_GENEXC)
								 + ".pbm"
                               );
            printPBMHeader(vout, 1, VCParameters.DISP_X,
                                    VCParameters.DISP_Y);
  
            Integer[][] vocab = VCGenerator.generateChallenge(
                                    vseed, useed, plain, null
                                );
            int[][] vpixels = VCGenerator.vcArrayToPixels(vocab);
  
            Integer[][] slide = VCGenerator.generateSlide( useed, null );
            int[][] spixels = VCGenerator.vcArrayToPixels(slide);
  
            for(int i = crpix-1; i < VCParameters.DISP_X-1; i += crpix){
            for(int r = 0; r < vpixels.length; r++) {
                vpixels[i][r] = VCParameters.black;
                vpixels[i+1][r] = VCParameters.black;
                vpixels[r][i] = VCParameters.black;
                vpixels[r][i+1] = VCParameters.black;
            }}
 
            for(int r = 0; r < vpixels.length; r++) {
                for(int c = 0; c < vpixels[r].length; c++) {
                    if(spixels[r][c] == VCParameters.black) {
                         vout.write('1');
                    } else {
                         vout.write(vpixels[r][c] == VCParameters.white ?
                                '0' : '1');
                    }
                }
                vout.write('\n');
            }
            vout.flush();
            vout.close();
        }

    }
}
