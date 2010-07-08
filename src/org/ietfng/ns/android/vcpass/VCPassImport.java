package org.ietfng.ns.android.vcpass;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public final class
VCPassImport
extends Activity
{
    private static final boolean TRY_QRCODE = false;

    /* * * * * * Protocol parameters * * * * * */

    private static final String DBGN = "VCPassImp";

    private static final String SAVED_SPAWNER = "SPAWN";

    public static final String ACTION_IMPORT_SEED
        = "org.ietfng.ns.android.vcpass.SEED_IMPORT";
    public static final String ACTION_IMPORT_AND_CREATE
        = "org.ietfng.ns.android.vcpass.IMPORT_AND_CREATE";

    private static final int REQ_IMPORT = 0;
    private static final int REQ_CREATE_FOR_PRESENT = 1;
    private static final int REQ_PRESENT = 2;
    private static final int REQ_CREATE = 3;

    /* EXTRAs are as in VCPassActivity */

    /* * * * * * Private state * * * * * */

    char[] useed;
    char[] vseed;
    Bitmap cfpc;
    String cfps;

    final String SAVED_STATE_USEED = "USEED";
    final String SAVED_STATE_VSEED = "VSEED";
    final String SAVED_STATE_CFPC  = "CFPC";
    final String SAVED_STATE_CFPS  = "CFPS";

    /* * * * * * Importing Seeds * * * * * */

    private void
    launchGenerator(int res, int min) {
        Log.d(DBGN, "Launching generator...");
        Intent intent = new Intent(this, VCPassActivity.class);
        intent.setAction(VCPassActivity.ACTION_CREATE_CHALLENGE);
        intent.putExtra(VCPassActivity.EXTRA_USER_SLIDE_SEED,useed);
        intent.putExtra(VCPassActivity.EXTRA_VOCABULARY_SEED,vseed);
        if(min > 0) {
            intent.putExtra(VCPassActivity.EXTRA_MINIMUM_EVENTS,min);
        }
        startActivityForResult(intent, res);
    }

    private void launchChallenger() {
        Log.d(DBGN, "Launching challenger...");
        Intent intent = new Intent(this, VCPassActivity.class);
        intent.setAction(VCPassActivity.ACTION_PRESENT_CHALLENGE);
        intent.putExtra(VCPassActivity.EXTRA_CHALLENGE,cfpc);
        intent.putExtra(VCPassActivity.EXTRA_PROMPT_TEXT,
                    getString(R.string.import_test));
        Log.d("VCPass", intent.toString());
        startActivityForResult(intent, REQ_PRESENT);
    }

    private static final class
    VCPassImpDoneHandler
    implements View.OnClickListener {
        private EditText et;
        private VCPassImport self;
        VCPassImpDoneHandler(EditText et, VCPassImport self) {
            this.et = et;
            this.self = self;
        }

        @Override
        public void onClick(View bv) {
            self.haveImportedSeed(et.getText().toString());
        }
    }

        // XXX
    private String zxingscan = "com.google.zxing.client.android.SCAN";
    private final void
    importSeed() {
        Log.d(DBGN, "Import seed...");

        Intent intent = new Intent(zxingscan);
        boolean isAvail = Utils.isIntentAvailable(this, intent);
        if(TRY_QRCODE && isAvail) {
            startActivityForResult(intent, REQ_IMPORT);
        } else {
            setContentView(R.layout.vcpimp);
            EditText set = (EditText) findViewById(R.id.password);
            Button donebtn = (Button) findViewById(R.id.impdone);
            donebtn.setOnClickListener(
                new VCPassImpDoneHandler(set, this));
        }
    }

    private final boolean
    haveImportedSeed(String seed) {
        Log.d(DBGN, "Have imported seed...");

        char[][] ds;
        try {
        ds = Utils.decode_seeds(seed);
        if(ds[0] == null
            || ds[1] == null
            || ds[0].length == 0
            || ds[1].length == 0)
            return false;
        } catch (Exception e) {
            return false;
        }

        this.useed = ds[0];
        this.vseed = ds[1];

        if(getIntent().hasExtra(VCPassActivity.EXTRA_QUIET_OPERATION)) {
            finishImportedSeed();
        } else {
            launchGenerator(REQ_CREATE_FOR_PRESENT, 1);
        }

        return true;
    }

    private final void
    finishImportedSeed() {
        Log.d(DBGN, "Finish imported seed...");
        if( getIntent().getAction().equals(ACTION_IMPORT_AND_CREATE) ) {
            launchGenerator(REQ_CREATE, 0);
        } else {
            finalResult(null,null);
        }
    }

    private final void
    finalResult(Parcelable chal, String secret) {
        Log.d(DBGN, "Final result...");

        Intent result = getIntent();

        result.putExtra(VCPassActivity.EXTRA_USER_SLIDE_SEED, useed  );
        result.putExtra(VCPassActivity.EXTRA_VOCABULARY_SEED, vseed  );
        if(chal != null)
            result.putExtra(VCPassActivity.EXTRA_CHALLENGE  , chal   );
        if(secret != null)
            result.putExtra(VCPassActivity.EXTRA_SECRET     , secret );
        setResult(RESULT_OK, result);
        finish();
    }

    public void onActivityResult(int req, int res, Intent data) {
        if(res == RESULT_CANCELED && req != REQ_PRESENT) {
           setResult(RESULT_CANCELED, getIntent());
        }
        if(data == null) {
            // being canceled; report failure upstream
            setResult(RESULT_CANCELED, null);
            finish();
            return;
        }

        switch(req) {
            case REQ_IMPORT:
                Log.d(DBGN, "Result IMPORT ...");
                if(!haveImportedSeed(data.getStringExtra("SCAN_RESULT"))){
                    // Something didn't work out; try again
                    importSeed();
                }
                break;
            case REQ_CREATE_FOR_PRESENT:
                Log.d(DBGN, "Result CFP ...");
                cfpc = (Bitmap) data.getParcelableExtra(
                        VCPassActivity.EXTRA_CHALLENGE);
                cfps = data.getStringExtra(VCPassActivity.EXTRA_SECRET);
                launchChallenger();
                break;
            case REQ_PRESENT:
                Log.d(DBGN, "Result P ...");
                // go back and redo import; the user can cancel that
                // if they really want to cancel.
                if ( res == RESULT_CANCELED ) {
                    importSeed();
                } else {
                    String sec = data.getStringExtra(
                        VCPassActivity.EXTRA_SECRET);
                    if(cfps.equals(sec)) {
                        finishImportedSeed();
                    } else {
                        launchChallenger();
                    }
                }
                break;
            case REQ_CREATE:
                Log.d(DBGN, "Result C ...");
                finalResult(data.getParcelableExtra(
                                VCPassActivity.EXTRA_CHALLENGE),
                            data.getStringExtra(
                                VCPassActivity.EXTRA_SECRET));
                break;
            default:
                throw new RuntimeException("Impossible req!");
        }
    }

    /* * * * * * Android interface core * * * * * */

    @Override
    public void onCreate(Bundle sis)
    {
        super.onCreate(sis);

        Log.i(DBGN, "CREATE");

        if(sis != null) {
            useed =          sis.getCharArray (SAVED_STATE_USEED);
            vseed =          sis.getCharArray (SAVED_STATE_VSEED);
            cfpc  = (Bitmap) sis.getParcelable(SAVED_STATE_CFPC );
            cfps  =          sis.getString    (SAVED_STATE_CFPS );
        }

        String act = getIntent().getAction();
        Log.d(DBGN, act);

        if (act.equals(ACTION_IMPORT_SEED)
                || act.equals(ACTION_IMPORT_AND_CREATE)) {
            importSeed();
        } else {
            setResult(RESULT_CANCELED, null);
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putCharArray (SAVED_STATE_USEED, useed);
        outState.putCharArray (SAVED_STATE_VSEED, vseed);

        outState.putParcelable(SAVED_STATE_CFPC , cfpc );
        outState.putString    (SAVED_STATE_CFPS , cfps );
    }

}
