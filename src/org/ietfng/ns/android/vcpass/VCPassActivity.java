/* Visual Cryptography Passcodes
 * (C) 2009 Nathaniel Filardo
 *
 * This code is available under the GPLv3 license.
 *
 * Much credit is owed to the excellent documentation and example code at
 * http://developer.android.com.
 */

package org.ietfng.ns.android.vcpass;

import java.io.Serializable;
import java.security.ProviderException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Formatter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
// import android.view.Menu;
// import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public final class
VCPassActivity
extends Activity
{
    /* * * * * * Protocol parameters * * * * * */

    private static final String DBGN = "VCPassAct";

    private static final String SAVED_STATE_KEY_RESPONSE
        = "SEQ";

        /** Intent ACTION name for challenging the user */
    public static final String ACTION_PRESENT_CHALLENGE
        = "org.ietfng.ns.android.vcpass.CHAL_PRESENT";
        /** Intent ACTION name for creating a challenge */
    public static final String ACTION_CREATE_CHALLENGE
        = "org.ietfng.ns.android.vcpass.CHAL_CREATE";

        /** Intent EXTRA name for error messages.
         *
         * Type: java.lang.String
         *
         * Used VCPA to caller for ACTION_*
         */
    public static final String EXTRA_ERROR
        = "ERR";
        /** Intent EXTRA name for prompt text.
         *
         * Type: java.lang.String
         *
         * Not used for ACTION_CREATE_CHALLENGE
         * Used caller to VCPA for ACTION_PRESENT_CHALLENGE
         */
    public static final String EXTRA_PROMPT_TEXT
        = "PT";
        /** Intent EXTRA name for plain-text response.
         *
         * Type: java.lang.String
         *
         * Used VCPA to caller for ACTION_CREATE_CHALLENGE
         * Used VCPA to caller for ACTION_PRESENT_CHALLENGE
         */
    public static final String EXTRA_SECRET
        = "SEQ";
        /** Intent EXTRA name for Bitmap challenge.
         *
         * Type: android.graphics.Bitmap
         *
         * Used VCPA to caller for ACTION_CREATE_CHALLENGE.
         * Used caller to VCPA for ACTION_PRESENT_CHALLENGE.
         */
    public static final String EXTRA_CHALLENGE
        = "CHAL";
        /** Intent EXTRA name for user slide seed.
         *
         * Type: [char
         *
         * Used caller to VCPA for ACTION_CREATE_CHALLENGE.
         * Not used for ACTION_PRESENT_CHALLENGE; 
         *      ideally, the contents would be unknown to the caller.
         * Used VCPA to caller for ACTION_IMPORT_SEED
         */
    public static final String EXTRA_USER_SLIDE_SEED
        = "USEED";
        /** Intent EXTRA name for vocabulary seed.
         *
         * Type: [char
         *
         * Used caller to VCPA for ACTION_CREATE_CHALLENGE.
         * Not used for ACTION_PRESENT_CHALLENGE; 
         *      ideally, the contents would be unknown to the caller.
         * Used VCPA to caller for ACTION_IMPORT_SEED
         */
    public static final String EXTRA_VOCABULARY_SEED
        = "VSEED";

        /** Intent EXTRA name for quiet operation.
         *
         * Type: void
         *
         * Used caller to VCPA for ACTION_CREATE_CHALLENGE
         * Not used for ACTION_PRESENT_CHALLENGE
         */
    public static final String EXTRA_QUIET_OPERATION = "QUIET";

        /** Intent EXTRA name for user slide seed.
         *
         * Type: int
         *
         * Used caller to VCPA for ACTION_CREATE_CHALLENGE.
         * Not used for ACTION_PRESENT_CHALLENGE
         */
    public static final String EXTRA_MINIMUM_EVENTS = "MINEVT";

    /* * * * * * Private constants * * * * * */

    private static final int cells = VCParameters.GRID_X
                                   * VCParameters.GRID_Y;
    private static final int crpix = VCParameters.DISP_X
                                   / VCParameters.GRID_X;
    private static final int ccpix = VCParameters.DISP_Y
                                   / VCParameters.GRID_Y;

    private static final int REQ_PRESENT_TEST   = 2;

    /* * * * * * Private state * * * * * */

    private Resources res;                     // Android
    private int[] response;                    // For state saving
    private Thread calcThread;

    /* * * * * * Private utility functions * * * * * */

    private final void
    yieldError(Intent s, String e) {
        s.putExtra(EXTRA_ERROR, e);
        setResult(RESULT_CANCELED, s);
        finish();
    }

    private final void
    postYieldError(final Intent s, final String e) {
        runOnUiThread(new Runnable(){
            public final void run() {
                yieldError(s,e);
            }
        });
    }

    /** Prepare an int array for use as a response store */
    private static final void
    resetResponse(int[] chal) {
        assert(chal.length == VCParameters.GRID_X*VCParameters.GRID_Y);

        for(int i = 0;
                i < VCParameters.GRID_X*VCParameters.GRID_Y;
                i++) {
            chal[i] = -1;
        }
    }

    /**
     * Render a challenge grid as plain text.
     *
     * Each cell of the array contains an index into the
     * vocabulary; the values [0,VCVOC_DISTINGUISHED] are
     * rendered into the resulting string.  The remainder
     * are ignored.
     */
    private static final StringBuilder
    encodeResponse(int[] chal) {
        assert(chal.length == VCParameters.GRID_X*VCParameters.GRID_Y);

        StringBuilder enc = new StringBuilder();

        for(int i = 0;
                i < VCParameters.GRID_X*VCParameters.GRID_Y;
                i++) {
            assert(chal[i] >= -1);
            assert(chal[i] <= VCParameters.VCVOC_SIZE);
            if(chal[i] >= 0
             && chal[i] <= VCParameters.VCVOC_DISTINGUISHED) {
                // enc.append(Integer.toString(i));
                enc.append(Integer.toString(chal[i]));
            } else {
                enc.append("_");
            }
        }

        return enc;
    }

    /* * * * * * Presenting a challenge to the user * * * * * */

    private final class
    VCPassPreDrawListener
    implements ViewTreeObserver.OnPreDrawListener { 
        private Intent i;
        private ImageView v;
        private Bitmap o;

        public VCPassPreDrawListener(Intent i, ImageView v, Bitmap o) {
            this.i = i;
            this.v = v;
            this.o = o;
        }

        public boolean onPreDraw() {
            // If the challenge will not fit, bail out.
            if (v.getWidth()  < o.getWidth()
             || v.getHeight() < o.getHeight()) {
                Log.d("AAAA", Integer.toString(v.getWidth()));
                Log.d("AAAA", Integer.toString(o.getWidth()));
                Log.d("AAAA", Integer.toString(v.getHeight()));
                Log.d("AAAA", Integer.toString(o.getHeight()));
                yieldError(i, "Challenge of incorrect size");
                return false;
            }

            return true;
        }
    }

    private final class
    VCPassTouchHandler
    implements View.OnTouchListener {
        private int[] r;
        private Canvas c;
        private Paint p;

        public VCPassTouchHandler(Canvas c, int[] r) {
            this.r = r;
            this.c = c;
            p = new Paint();
            p.setARGB(127,0,255,0);
        }

        private int expected_motion = MotionEvent.ACTION_DOWN;
        private float last_down_xc = -1;
        private float last_down_yc = -1;
        private int last_down_x = -1;
        private int last_down_y = -1;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            float w = v.getWidth();
            float h = v.getHeight();
            float xc = event.getX();
            float yc = event.getY();

            if (action != expected_motion)
                return true;

            int x = (int)(xc / w * VCParameters.GRID_X);
            int y = (int)(yc / h * VCParameters.GRID_Y);

            if( x < 0 || y < 0 )
                return true;


            if (action == MotionEvent.ACTION_DOWN) {
                last_down_xc = xc;
                last_down_yc = yc;
                last_down_x = x;
                last_down_y = y;
                expected_motion = MotionEvent.ACTION_UP;
            } else if (action == MotionEvent.ACTION_UP) {
                float deltaX = xc - last_down_xc;
                float deltaY = yc - last_down_yc;

                int label;
                assert(VCParameters.VCVOC_DISTINGUISHED == 3);
                if ( Math.abs(deltaX) > Math.abs(deltaY) ) {
                    if ( deltaX > 0 ) {
                        label = VCParameters.VCVOC_DISTING_RIGHT;
                    } else {
                        label = VCParameters.VCVOC_DISTING_LEFT;
                    }
                } else {
                    if ( deltaY > 0 ) {
                        label = VCParameters.VCVOC_DISTING_DOWN;
                    } else {
                        label = VCParameters.VCVOC_DISTING_UP;
                    }
                }
                r[VCParameters.GRID_X*last_down_y
                 + last_down_x] = label;

                expected_motion = MotionEvent.ACTION_DOWN;

                Rect sq = new Rect((int)(last_down_x*w
                                        /VCParameters.GRID_X
                                        ),
                                    (int)(last_down_y*h
                                         /VCParameters.GRID_Y
                                         ),
                                    (int)((last_down_x+1)*w
                                         /VCParameters.GRID_X
                                         ),
                                    (int)((last_down_y+1)*h
                                         /VCParameters.GRID_Y
                                         ));
                Log.d("VCPTH", encodeResponse(r).toString());
                c.drawRect(sq, p);
                v.invalidate();
            } else {
                throw new RuntimeException("Unexpected action:" + action + "\n");
            }

            return true;
        }
    }

    private static final class
    VCPassResetHandler
    implements View.OnClickListener {
        private ImageView v;
        private Canvas c;
        private Bitmap o;
        private int[] r;

        VCPassResetHandler(Canvas c, ImageView v, Bitmap o, int[] r)
        {
            this.c = c;
            this.v = v;
            this.o = o;
            this.r = r;
        }

        @Override
        public void onClick(View bv) {
            Bitmap nb = o.copy(o.getConfig(), true);
            // s.delete(0,s.length());
            resetResponse(r);
            c.setBitmap(nb);
            v.setImageBitmap(nb);
        }
    }

    private final class
    VCPassCompletionHandler
    implements View.OnClickListener {
        private Intent i;
        private int[] r;

        VCPassCompletionHandler(Intent i, int[] r)
        {
            this.i = i;
            this.r = r;
        }

        @Override
        public void onClick(View bv) {
            i.putExtra(EXTRA_SECRET, encodeResponse(r).toString());
            setResult(RESULT_OK, i);
            finish();
        }
    }


    private final void
    presentChallenge(Intent spawner, Bundle sis) {
        response = null;
        if (sis != null) {
            response = sis.getIntArray(SAVED_STATE_KEY_RESPONSE);
        }
        if(response == null) {
            response = new int[VCParameters.GRID_X
                              *VCParameters.GRID_Y];
            resetResponse(response);
        }


        Bitmap origchal = (Bitmap)spawner.getParcelableExtra(EXTRA_CHALLENGE);
        if (null == origchal) {
            yieldError(spawner, "No Challenge Given");
            return;
        }

        /*
         * XXX Do we have to draw blanks here?  Does our graphic state
         * get saved for us?
         */

        Bitmap chal = origchal.copy(origchal.getConfig(), true);
        Canvas c = new Canvas(chal);

        // Switch off the titlebar
        // requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Lock orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // Load layout
        setContentView(R.layout.vcpact);

        ImageView imgview = (ImageView) findViewById(R.id.image);
        imgview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imgview.setImageBitmap(chal);
        imgview.setOnTouchListener(new VCPassTouchHandler(c, response));
        imgview.getViewTreeObserver()
               .addOnPreDrawListener(
                   new VCPassPreDrawListener(spawner, imgview, origchal));

        Button resetbtn = (Button) findViewById(R.id.reset);
        resetbtn.setOnClickListener(
            new VCPassResetHandler(c, imgview, origchal, response));
        Button donebtn = (Button) findViewById(R.id.done);
        donebtn.setOnClickListener(
            new VCPassCompletionHandler(spawner, response));

        String pt = spawner.getStringExtra(EXTRA_PROMPT_TEXT);
        if(pt != null) {
            TextView pttv = (TextView) findViewById(R.id.prompttext);
            pttv.setText(pt);
        }
    }

    /* * * * * * Creating a challenge * * * * * */

	public static class CreatedChallenge {
		/* Have I mentioned recently how much I hate Java?
		 * This is as close to error+plain*bm as is trivial
		 * to make here.
		 */
    	public String plain;
		public Bitmap bm;
		public String error;
	}

	public static CreatedChallenge
	do_createChallenge(
		char[] useed,
		char[] vseed,
		int minevt,
		VCGenerator.ProgCallback pcb
	) {
		CreatedChallenge res = new CreatedChallenge();
        	int[] plain = new int[cells];

        try {
            // Let this one initialize from the system's random source
            SecureRandom chalsr = new SecureRandom();
            int evtc = 0;
            do {
                evtc = 0;
                for(int i = 0; i < cells; i++) {
                    plain[i] = chalsr.nextInt(VCParameters.VCVOC_SIZE+1);
                    if(plain[i] <= VCParameters.VCVOC_DISTINGUISHED) {
                        evtc++;
                    }
                }
            } while (evtc < minevt);

            res.plain = encodeResponse(plain).toString();
            
            Integer[][] cella = VCGenerator.generateChallenge(
                                    vseed, useed,
                                    plain, pcb
                                );
            if(cella == null) {
                res.error = "Null return from generator";
                return res;
            }

            assert(cella.length == cells);
            assert(cella[0].length == crpix*ccpix);

            res.bm = Bitmap.createBitmap(
                        VCParameters.DISP_X,
                        VCParameters.DISP_Y,
                        Bitmap.Config.RGB_565);

            for(int i = 0; i < cells; i++) {
                int[] cellai = new int[cella[i].length];
                for(int j = 0; j < cella[i].length; j++) {
                    cellai[j] = cella[i][j];
                }

                res.bm.setPixels(cellai, 0,
                                crpix,
                                (i%VCParameters.GRID_X)*crpix,
                                (i/VCParameters.GRID_Y)*ccpix,
                                crpix, ccpix
                               );
            }

/* Alternative, row-by-row formulation:
        int[][] pixels = VCGenerator.vcArrayToPixels(cella);
        for(int i = 0; i < pixels.length; i++) {
            res.bm.setPixels(pixels[i], 0, pixels[i].length, 0, i, pixels[i].length, 1);
        }
*/

            Paint p = new Paint();
            p.setColor(0xC0FFFF00);

            Canvas c = new Canvas(res.bm);
            for(int i = crpix-1; i < VCParameters.DISP_X-1; i += crpix){
                c.drawLine(i,0,i,VCParameters.DISP_Y,p);
                c.drawLine(i+1,0,i+1,VCParameters.DISP_Y,p);
            }
            for(int i = ccpix-1; i < VCParameters.DISP_Y-1; i += ccpix){
                c.drawLine(0,i,VCParameters.DISP_X,i,p);
                c.drawLine(0,i+1,VCParameters.DISP_X,i+1,p);
            }

        } catch (ProviderException pe) {
			res.error = pe.toString();
            return res;
        } catch (GeneralSecurityException gse) {
			res.error = gse.toString();
            return res;
        }

/*
            Paint p = new Paint();
            Canvas c = new Canvas(bm);
            p.setTextSize(20);

            StringBuilder sb = new StringBuilder();
            Formatter f = new Formatter(sb);
            f.format("%f", p.getFontMetrics().top);
            Log.d("VCPassPDH", sb.toString());

            c.drawText("1", 1.0f, 1.0f, p);
            c.drawText("30", 30.0f, 30.0f, p);
            c.drawText("70", 70.0f, 70.0f, p);
*/

		return res;

	}

    private final void
    _intent_createChallenge(final Intent spawner,
                        boolean quiet) {
        char[] useed = spawner.getCharArrayExtra(EXTRA_USER_SLIDE_SEED   );
        char[] vseed = spawner.getCharArrayExtra(EXTRA_VOCABULARY_SEED   );
        int minevt   = spawner.getIntExtra      (EXTRA_MINIMUM_EVENTS, -1);

        if(useed == null || vseed == null) {
            postYieldError(spawner, "Null seed");
            return;
        }

        final VCGenerator.ProgCallback pcb
        = quiet ? null : new VCGenerator.ProgCallback() {
            public void progress(final int x) {
                    runOnUiThread(new Runnable() {
                        public final void run() {
                            getWindow().setFeatureInt(
                                            Window.FEATURE_PROGRESS,
                                            x*9000
                                            /VCParameters.GRID_X
                                            /VCParameters.GRID_Y);
                        }
                   });
            }
        };

		CreatedChallenge cc = do_createChallenge(useed, vseed, minevt, pcb);
		if(cc.error != null) {
        	postYieldError(spawner, cc.error);
			return;
		}

        spawner.putExtra(EXTRA_CHALLENGE, (Parcelable)cc.bm);
        spawner.putExtra(EXTRA_SECRET, cc.plain);

        calcThread = null;

        runOnUiThread(new Runnable() {
            final public void run() {
                setResult(RESULT_OK, spawner);
                finish();
            }
        });
    }

    private final void
    createChallenge(final Intent spawner, Bundle sis) {
        final boolean quiet = spawner.hasExtra(EXTRA_QUIET_OPERATION);

        if(!quiet) {
            requestWindowFeature(Window.FEATURE_PROGRESS);
            getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);

            TextView tv = new TextView(this);
            tv.setText("Please wait while I do some math...");
            setContentView(tv);
        }

        calcThread = new Thread(new Runnable() {
            public final void run() {
                _intent_createChallenge(spawner, quiet);
            }
        });

        calcThread.start();
    }

    /* * * * * * Android interface core * * * * * */

    @Override
    public void onCreate(Bundle sis)
    {
        super.onCreate(sis);

        Log.i(DBGN, "CREATE");

        res = getResources();

        Intent spawner = getIntent();
        String act = spawner.getAction();
        Log.d(DBGN, act);

        if(act.equals(ACTION_PRESENT_CHALLENGE)) {
            presentChallenge(spawner, sis);
        } else if (act.equals(ACTION_CREATE_CHALLENGE)) {
            createChallenge(spawner, sis);
        } else {
            setResult(RESULT_CANCELED, null);
            finish();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if(isFinishing()) {
            if(calcThread != null) { calcThread.interrupt(); }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        /* If we were presenting a challenge this time around,
         * store our secret so that we can come back to it
         */
        if(getIntent().getAction().equals(ACTION_PRESENT_CHALLENGE)) {
            outState.putIntArray(SAVED_STATE_KEY_RESPONSE, response);
        }
    }
}
