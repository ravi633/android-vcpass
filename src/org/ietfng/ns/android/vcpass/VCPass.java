/* Visual Cryptography Passcodes
 * (C) 2009 Nathaniel Filardo
 *
 * This code is available under the GPLv3 license.
 *
 * Much credit is owed to the excellent documentation and example code at
 * http://developer.android.com.
 */

package org.ietfng.ns.android.vcpass;

import java.lang.reflect.Field; 
import java.lang.reflect.Method; 
import java.io.Serializable;
import java.security.Provider;
import java.util.Formatter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import dalvik.system.PathClassLoader;


// drawing
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

// import com.google.zxing.client.android.Intents;

public class VCPass extends Activity
{
    private boolean canceled = false;
    private boolean inited = false;
    private char[] useed;
    private char[] vseed;
    private String secret;
    private Bitmap chal;

    private static final int REQ_IMPORT = 0;
    private static final int REQ_CREATE = 1;
    private static final int REQ_PRESENT = 2;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Log.i("VCPass", "CREATE");


		if(false) {
			PackageManager pm = getPackageManager();
        	Intent intent = new Intent("org.ietfng.ns.android.vcpass.CHAL_PRESENT");
			ResolveInfo ri = pm.resolveActivity(intent, 0);
			PathClassLoader pcl = new PathClassLoader
				(ri.activityInfo.applicationInfo.publicSourceDir
				,getClassLoader()
			);

			Class c;
			try{	
				c = pcl.loadClass(ri.activityInfo.name);
				Log.i("VCPass:CREATE:c", c.toString());
			} catch (ClassNotFoundException cnfe) {
				Log.i("VCPass:CREATE:c", "CNFE" + cnfe.toString());
				return;
			}

			/* This loop is dumb because Java clearly knows better than we do
			 * but specifying the parameter types is a pain.  If something goes
			 * wrong, it'll get caught by reflection later.
			 */
			Method m = null;
			for(Method mc : c.getMethods()) {
				if(mc.getName().equals("do_createChallenge")) { m = mc; break; }
			}
			if(m == null) {
				Log.i("VCPass:CREATE:m", "NOT FOUND");
				return;
			} else {
				Log.i("VCPass:CREATE:m", m.toString());
			}

			Object r;
			try {
				r = m.invoke(null, "foo".toCharArray(),
									"bar".toCharArray(),
									new Integer(0),
									null);
				Log.i("VCPass:CREATE:r", r.toString());
			} catch (IllegalAccessException iae) {
				Log.i("VCPass:CREATE:r", "IAE" +  iae.toString());
				return;
			} catch (java.lang.reflect.InvocationTargetException ite) {
				Log.i("VCPass:CREATE:r", "ITE" +  ite.toString());
				return;
			}

			try {
				Field fe = r.getClass().getField("error");
				String e = (String) fe.get(r);
				Field fp = r.getClass().getField("plain");
				String p = (String) fp.get(r);
				Field fb = r.getClass().getField("bm");
				Bitmap b = (Bitmap) fb.get(r);

				if(e != null)
					Log.i("VCPass:CREATE:e", e);
				else {
					Log.i("VCPass:CREATE:b", b.toString());
					Log.i("VCPass:CREATE:p", p);
				}
			} catch (IllegalAccessException iae) {
				Log.i("VCPass:CREATE:_", "IAE" +  iae.toString());
				return;
			} catch (NoSuchFieldException nsfe) {
				Log.i("VCPass:CREATE:_", "NSFE" +  nsfe.toString());
				return;
			}
		}

/*
        for (String s : Security.getAlgorithms("cipher")) {
            Log.d ("VCPassCrypto", s);
        }
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException e) {
            Log.d("VCPassCrypto", "Argh NSA");
        } catch (NoSuchPaddingException e) {
            Log.d("VCPassCrypto", "Argh NSP");
        }
*/

    }


    private void launchImporter() {
        Intent intent = new Intent(this, VCPassImport.class);
        intent.setAction(VCPassImport.ACTION_IMPORT_SEED);
        startActivityForResult(intent, REQ_IMPORT);
    }

    private void launchGenerator() {
        Intent intent = new Intent(this, VCPassActivity.class);
        intent.setAction(VCPassActivity.ACTION_CREATE_CHALLENGE);
        intent.putExtra(VCPassActivity.EXTRA_USER_SLIDE_SEED,useed);
        intent.putExtra(VCPassActivity.EXTRA_VOCABULARY_SEED,vseed);
        // intent.putExtra(VCPassActivity.EXTRA_QUIET_OPERATION,true);
        Log.d("VCPass", intent.toString());
        startActivityForResult(intent, REQ_CREATE);
    }

    private void launchChallenger() {
        Intent intent = new Intent(this, VCPassActivity.class);
        intent.setAction(VCPassActivity.ACTION_PRESENT_CHALLENGE);
        intent.putExtra(VCPassActivity.EXTRA_CHALLENGE,chal);
        intent.putExtra(VCPassActivity.EXTRA_PROMPT_TEXT,"Try this one...");
        Log.d("VCPass", intent.toString());
        startActivityForResult(intent, REQ_PRESENT);
    }

    public void onResume()
    {
        super.onResume();

        Log.i("VCPass", "RESUME");

        if(!inited) {
            inited = true;
	    launchImporter();
        }
    }

    public void onActivityResult(int req, int res, Intent data)
    {
        Log.i("VCPass", "OAR");
        Log.i("VCPass", Integer.toString(req));

        if(data != null)
            Log.i("VCPass", data.getAction());

        if(res == RESULT_CANCELED) {
            Log.d("VCPass", "CANCELED");
            canceled = true;
            String err = null;
            if(data != null)
               err = data.getStringExtra(VCPassActivity.EXTRA_ERROR);
            if(err != null)
                Log.i("VCPass", err);
	    finish();
        } else {
            if (req == REQ_IMPORT) {
		useed = data.getCharArrayExtra(VCPassActivity.EXTRA_USER_SLIDE_SEED);
		vseed = data.getCharArrayExtra(VCPassActivity.EXTRA_VOCABULARY_SEED);

            	launchGenerator();
            } else if(req == REQ_CREATE) {
                chal = (Bitmap) data.getParcelableExtra(VCPassActivity.EXTRA_CHALLENGE);
                secret = data.getStringExtra(VCPassActivity.EXTRA_SECRET);
                Log.i("VCPass", "CREATECHAL: " + secret);

            	launchChallenger();
            } else if (req == REQ_PRESENT) {
                String s = data.getStringExtra(VCPassActivity.EXTRA_SECRET);
                if(s.equals(secret)) {
                    Log.i("VCPass","correct");
		    launchGenerator();
                } else {
		    launchChallenger();
		}
            } else {
                throw new RuntimeException("Invalid request!");
            }
        }
    }
}
