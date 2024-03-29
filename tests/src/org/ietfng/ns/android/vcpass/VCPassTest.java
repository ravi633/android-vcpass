package org.ietfng.ns.android.vcpass;

import android.test.ActivityInstrumentationTestCase;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class org.ietfng.ns.android.vcpass.VCPassTest \
 * org.ietfng.ns.android.vcpass.tests/android.test.InstrumentationTestRunner
 */
public class VCPassTest extends ActivityInstrumentationTestCase<VCPass> {

    public VCPassTest() {
        super("org.ietfng.ns.android.vcpass", VCPass.class);
    }

}
