/** Code for generating visual cryptography slides and challenges.
 * @author Nathaniel Filardo
 * @license AGPLv3.
 */

package org.ietfng.ns.android.vcpass;

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.ProviderException;

import java.util.Stack;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public final class VCGenerator {

    public interface ProgCallback {
        public void progress(int x);
    }

    private static final int cells = VCParameters.GRID_X
                                   * VCParameters.GRID_Y;
    private static final int cpix = VCParameters.DISP_X
                                  * VCParameters.DISP_Y
                                  / VCParameters.GRID_X
                                  / VCParameters.GRID_Y;
    private static final int crvpix = VCParameters.DISP_X
                                    / VCParameters.GRID_X
                                    / VCParameters.PR_X;
    private static final int ccvpix = VCParameters.DISP_Y
                                    / VCParameters.GRID_Y
                                    / VCParameters.PR_Y;
    private static final int crpix = VCParameters.DISP_X
                                   / VCParameters.GRID_X;
    private static final int ccpix = VCParameters.DISP_Y
                                   / VCParameters.GRID_Y;

    private static final void
    _bitsToVCPixels(Stack<Integer> r,
                    Integer c1,
                    Integer c2,
                    byte[] bits) {

        for(int i = 0; i < crvpix/8; i++) {
            byte by = bits[i];
            for (int b = 0; b < 8; b++) {
                boolean bi = !((by & 0x80) == 0);
                by <<= 1;

                for(int px = 0; px < VCParameters.PR_X; px++) {
                    if(bi) { r.push(c1); }
                    else   { r.push(c2); }
                    Integer ct; ct = c1; c1 = c2; c2 = ct;
                }
            }
        }

        byte by = bits[(crvpix+7)/8 - 1];
        for (int b = 0; b < crvpix%8; b++) {
            boolean bi = !((by & 0x80) == 0);
            by <<= 1;

            for(int px = 0; px < VCParameters.PR_X; px++) {
                if(bi) { r.push(c1); }
                else   { r.push(c2); }
                Integer ct; ct = c1; c1 = c2; c2 = ct;
            }
        }
    }

    private static final Vector<Integer>
    bitsToVCPixels(byte[] bits) {
        Stack<Integer> r = new Stack<Integer>();
        int py;
        for(py = 0; py < VCParameters.PR_Y/2; py++) {
            _bitsToVCPixels(r, VCParameters.black, VCParameters.white, bits);
        }
        for(; py < VCParameters.PR_Y; py++) {
            _bitsToVCPixels(r, VCParameters.white, VCParameters.black, bits);
        }

        return r;
    }

/*
    private static final void
    mapXor(byte[] a, byte[] b) {
        assert(a.length == b.length);

        for (int i = 0; i < a.length; i++) {
            a[i] ^= b[i];
        }
    }
*/

    /*
     * The CSPRNGs are clocked as follows:
     *      For each cell,
     *          For each row,
     *              For each vocabulary entry,
     *                  generate (CPIXX+7)/8 octets
     *
     * The byte array is consulted in ascending index order and
     * bits within bytes are mapped to pixels LSB to MSB as left
     * to right.
     *
     * Note that CSPRNG output bits may be lost if there are
     * not an integer multiple of 8 vcpixels per row.
     *
     * Note that the user's slide does not have the "for each
     * vocabulary entry" iteration.
     *
     * Even the distinguished vocabulary elements' owned pixels
     * are clocked out of the CSPRNGs just for simplicity.
     *
     * The total number of bits read out for the user's slide is
     *      GRID_X*GRID_Y*CPIXY*CPIXX
     * or
     *      4*4*160*160 bits = 409600 bits = 51200 bytes
     * and for the challenge is
     *      VCVOC_SIZE*...
     * or
     *      16*4*4*160*160 bits = 6553600 bits = 819200 bytes
     */ 

    public final static Integer[][]
    generateSlide(
        final char[] useed,
        final ProgCallback pcb
    ) throws
        ProviderException, GeneralSecurityException
    {
        Cipher slidec = seedToCipher(useed);

        Integer[][] cella = new Integer[cells][];

        for(int i = 0; i < cells; i++) {
            Vector<Integer> cell = new Vector<Integer>();
            for(int j = 0; j < ccvpix; j++) {
                byte[] srow = new byte[(crvpix+7)/8];
                srow = slidec.update(srow);
                assert(srow.length == (crvpix+7)/8);
                Vector<Integer> srowvc = bitsToVCPixels(srow);
                cell.addAll(srowvc);
            } /* Row */
            cella[i] = new Integer[cell.size()];
            cell.toArray(cella[i]);
        } /* Cell */

        return cella;
    }

    public final static Integer[][] generateChallenge(
        final char[] vseed,
        final char[] useed,
        final int[] plain,
        final ProgCallback pcb
    ) throws
        ProviderException, GeneralSecurityException
    {
        Cipher slidec = seedToCipher(useed);
        Cipher cellc = seedToCipher(vseed);

        Integer[][] cella = new Integer[cells][];

        for(int i = 0; i < cells; i++) {
            if(Thread.interrupted()) {
                return null;
            }
			assert(plain[i] < VCParameters.VCVOC_SIZE);

            Vector<Integer> cell = new Vector<Integer>();
            for(int j = 0; j < ccvpix; j++) {
                byte[] srow = new byte[(crvpix+7)/8];
                srow = slidec.update(srow);
                assert(srow.length == (crvpix+7)/8);

                byte[] vrow = null;
                /* Get the right bits into vrow */
                for(int k = 0; k <= plain[i]; k++) {
                    vrow = new byte[(crvpix+7)/8];
                    vrow = cellc.update(vrow);
                    assert(vrow.length == (crvpix+7)/8);
                }

                if(plain[i] <= VCParameters.VCVOC_DISTINGUISHED) {
                    /* Set the owned pixels in this row to
                     * match those of srow; mutates vrow in place.
                     */

                    /* This design works for square cells */
                    assert(crvpix == ccvpix);

                    final int lix = j/8;
                    final int lbm
                        = (j%8 == 0)
                        ? 0
                        : ((byte)0x80) >> ((j%8)-1);

                    final int rix = (ccvpix+7-j)/8-1;
                    final int rbm
                        = ((ccvpix-j)%8 == 0)
                        ? 0
                        : ~(((byte)0x80) >> ((ccvpix+7-j)%8));

                    switch(plain[i]) {
                        case VCParameters.VCVOC_DISTING_DOWN:
                            if (j < ccvpix/2) {
                                if(lix == rix) {
                                    vrow[lix] &= lbm | rbm;
                                    vrow[lix] |= srow[lix] & ~(lbm | rbm);
                                } else {
                                    vrow[lix] &= lbm;
                                    vrow[lix] |= srow[lix] & ~lbm;
                                    vrow[rix] &= rbm;
                                    vrow[rix] |= srow[rix] & ~rbm;
                                }
                            }
                            for(int k = lix+1; k < rix; k++)
                                vrow[k] = srow[k];
                            break;
                        case VCParameters.VCVOC_DISTING_UP:
                            if (j > ccvpix/2) {
                                if(lix == rix) {
                                    vrow[lix] &= ~lbm | ~rbm;
                                    vrow[lix] |= srow[lix] & (lbm & rbm);
                                } else {
                                    vrow[lix] &= ~lbm;
                                    vrow[lix] |= srow[lix] & lbm;
                                    vrow[rix] &= ~rbm;
                                    vrow[rix] |= srow[rix] & rbm;
                                }
                            }
                            for(int k = rix+1; k < lix; k++)
                                vrow[k] = srow[k];
                            break;
                        case VCParameters.VCVOC_DISTING_RIGHT:
                            if (j < ccvpix/2) {
                                vrow[lix] &= ~lbm;
                                vrow[lix] |= srow[lix] & lbm;
                                for(int k = 0; k < lix; k++)
                                    vrow[k] = srow[k];
                            } else {
                                vrow[rix] &= rbm;
                                vrow[rix] |= srow[rix] & ~rbm;
                                for(int k = 0; k < rix; k++)
                                    vrow[k] = srow[k];
                            }
                            break;
                        case VCParameters.VCVOC_DISTING_LEFT:
                            if (j < ccvpix/2) {
                                vrow[rix] &= ~rbm;
                                vrow[rix] |= srow[rix] & rbm;
                                for(int k = rix+1; k < (ccvpix+7)/8; k++)
                                    vrow[k] = srow[k];
                            } else {
                                vrow[lix] &= lbm;
                                vrow[lix] |= srow[lix] & ~lbm;
                                for(int k = lix+1; k < (ccvpix+7)/8; k++)
                                    vrow[k] = srow[k];
                            }
                            break;
                    }
                }

                /* Set pixels in resulting image */
                Vector<Integer> vrowvc = bitsToVCPixels(vrow);
                cell.addAll(vrowvc);
                
                /* Drain the remaining bits from the CSPRNG */
                for(int k = plain[i] + 1;
                        k < VCParameters.VCVOC_SIZE;
                        k++) {
                    vrow = new byte[(crvpix+7)/8];
                    vrow = cellc.update(vrow);
                    assert(vrow.length == (crvpix+7)/8);
                }
            } /* Row */
            cella[i] = new Integer[cell.size()];
            cell.toArray(cella[i]);

            if (pcb != null) { pcb.progress(i); }
        } /* Cell */

        return cella;
    }

    public final static int[][] vcArrayToPixels(Integer[][] p) {
        assert(p.length == cells);
        for(int i = 0; i < cells; i++) {
            assert(p[i].length == crpix*ccpix);
        }

        int[][] res = new int[VCParameters.DISP_Y][];
        for(int r = 0; r < VCParameters.DISP_Y; r++) {
            res[r] = new int[VCParameters.DISP_X];

            for(int c = 0; c < VCParameters.DISP_X; c++) {
                final int cell =
                    r/crpix*VCParameters.GRID_X + (c/ccpix);
                final int pixoff =
                    (r%crpix)*ccpix + c%ccpix;
                res[r][c] = p[cell][pixoff];
            }
        }

        return res;
    }

    private static final Cipher
    seedToCipher(final char[] seed)
    throws ProviderException, GeneralSecurityException
    {
        SecretKeyFactory skf =
            SecretKeyFactory.getInstance(VCParameters.CSKEYFACT,
                                         VCParameters.CSPROV);

        byte[] salt = { 0x22, 0x24 };
        PBEKeySpec pks = new PBEKeySpec(seed, salt, 1024);
        SecretKey sk = skf.generateSecret(pks);

        Cipher c = Cipher.getInstance(VCParameters.CSPRNG,
                                      VCParameters.CSPROV);  
        c.init(Cipher.ENCRYPT_MODE, sk);

        return c;
    }
}

