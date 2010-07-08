package org.ietfng.ns.android.vcpass;

import java.util.List;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

public class Utils {
    /** From http://developer.android.com/resources/articles/can-i-use-this-intent.html */
    public final static boolean isIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
            packageManager.queryIntentActivities(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

        /*
         * DJB Netstrings-esque encoding functions for storing
         * our two seeds in one string.
         */
        public static String encode_seeds(char[] useed, char[] vseed) {
                StringBuilder seed = new StringBuilder();
                seed.append(Integer.toString(useed.length));
                seed.append(':');
                seed.append(useed);
                seed.append(Integer.toString(vseed.length));
                seed.append(':');
                seed.append(vseed);
                return seed.toString();
        }

        public static char[][] decode_seeds(String s) {
                StringBuilder sb = new StringBuilder(s);
                int ulenend = sb.indexOf(":");
                int ulen = Integer.parseInt(sb.substring(0,ulenend));
                int vlenend = sb.indexOf(":",ulenend+ulen+1);
                int vlen = Integer.parseInt(sb.substring(ulenend+ulen+1,vlenend));
                char[][] ret = new char[2][];
                ret[0] = sb.substring(ulenend+1,ulenend+ulen+1).toCharArray();
                ret[1] = sb.substring(vlenend+1).toCharArray();
                return ret;
        }

}
