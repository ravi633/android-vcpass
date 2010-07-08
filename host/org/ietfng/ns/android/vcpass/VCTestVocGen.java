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
 *            host/org/ietfng/ns/android/vcpass/VCTestVocGen.java
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

public final class VCTestVocGen {
    static final private int SEED_SIZE = 16;

    private static final int QR_SIZEX = 400;
    private static final int QR_SIZEY = 400;

    static final private Options cliopts = new Options();
    static final private String OPT_SS_HELP   = "h";
    static final private String OPT_SS_SECRET = "S";
    static final private String OPT_SS_VOCSEC = "V";
    static final private String OPT_SS_GENEXC = "c";
    static final private String OPT_SS_GENEXV = "v";

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
        cliopts.addOption(OPT_SS_SECRET, "secretseed", true,
                            "Secret seed (for development)");
        cliopts.addOption(OPT_SS_SECRET, "slideseed", true,
                            "Slide seed (for development)");
        cliopts.addOption(OPT_SS_VOCSEC, "vocseed", true,
                            "Vocabulary seed (for development)");
        cliopts.addOption(OPT_SS_GENEXC, "chalfile", true,
                            "Example challenge file name");
        cliopts.addOption(OPT_SS_GENEXV, "vocfile", true,
                            "Example vocabulary file name");
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

        char[] useed;
        if(cmd.hasOption(OPT_SS_SECRET)) {
            useed = cmd.getOptionValue(OPT_SS_SECRET).toCharArray();
        } else {
            // XXX
            useed = "foo".toCharArray();
        }

        char[] vseed;
        if(cmd.hasOption(OPT_SS_VOCSEC)) {
            vseed = cmd.getOptionValue(OPT_SS_VOCSEC).toCharArray();
        } else {
            // XXX
            vseed = "bar".toCharArray();
        }

        if(cmd.hasOption(OPT_SS_GENEXV)) {
            Integer[][] vocab = new Integer[VCParameters.VCVOC_SIZE][];
            Integer[][] vslide = new Integer[VCParameters.VCVOC_SIZE][];

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
                               );
            printPBMHeader(vout, 1, VCParameters.DISP_X,
                                    VCParameters.DISP_Y);

            /* XXX */ 
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
