"""Guard parsing against the actual Android 35/36 activity dump format."""
import unittest

from verify_signed_runtime import COMPONENT, saved_stopped_activity


class SavedActivityStateTest(unittest.TestCase):
    def dump(self, target_state="STOPPED", saved="true"):
        return f"""ACTIVITY MANAGER ACTIVITIES
      * Hist  #1: ActivityRecord{{home u0 com.android.launcher/.Main t6}}
        mHaveState=true mIcicle=Bundle[mParcelledData.dataSize=100]
        state=STOPPED delayedResume=false finishing=false
    mLastPausedActivity: ActivityRecord{{app u0 {COMPONENT} t10}}
    * Hist  #0: ActivityRecord{{app u0 {COMPONENT} t10}}
      mHaveState={saved} mIcicle=Bundle[mParcelledData.dataSize=5396]
      state={target_state} delayedResume=false finishing=false
"""

    def test_current_dump_recognizes_target_saved_state(self):
        self.assertTrue(saved_stopped_activity(self.dump(), COMPONENT))

    def test_neighboring_launcher_state_cannot_validate_target(self):
        self.assertFalse(saved_stopped_activity(self.dump(target_state="RESUMED"), COMPONENT))
        self.assertFalse(saved_stopped_activity(self.dump(saved="false"), COMPONENT))

    def test_reference_outside_hist_is_not_target_activity(self):
        state = self.dump().split("    * Hist  #0:")[0]
        self.assertFalse(saved_stopped_activity(state, COMPONENT))


if __name__ == "__main__":
    unittest.main()
