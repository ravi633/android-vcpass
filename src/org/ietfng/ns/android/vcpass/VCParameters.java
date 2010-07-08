package org.ietfng.ns.android.vcpass;

import java.security.Provider;
import javax.crypto.spec.PBEKeySpec;

public class VCParameters {
        /* CSPRNG Class name */
    static final Provider CSPROV =
        new org.bouncycastle.jce.provider.BouncyCastleProvider();
    static final String CSPRNG = "AES/CFB8/NoPadding";
    static final String CSKEYFACT = "PBEWithSHAAnd128BitAES-CBC-BC";

        /* Challenge grid size */
    static final int GRID_X = 4;
    static final int GRID_Y = 4;

        /* Real pixels per VC Pixel */
    static final int PR_X = 2;
    static final int PR_Y = 2;

        /* Display size, in real pixels */
    static final int DISP_X = 320;
    static final int DISP_Y = 320;

        /** Number of entries in the per-cell vocabulary */
    static final int VCVOC_SIZE = 6;
        /** Last index of distinguished entries in the per-cell vocabulary.
         * @see VCPassActivity.VCPassTouchHandler.onTouch
         * @see VCGenerator.createChallenge
         */
    static final int VCVOC_DISTINGUISHED = 3;

    static final int VCVOC_DISTING_UP    = 0;
    static final int VCVOC_DISTING_DOWN  = 1;
    static final int VCVOC_DISTING_LEFT  = 2;
    static final int VCVOC_DISTING_RIGHT = 3;

        /** Pixel values */
    static final Integer black = 0xFF000000;
    static final Integer white = 0xFFFFFFFF;
};
