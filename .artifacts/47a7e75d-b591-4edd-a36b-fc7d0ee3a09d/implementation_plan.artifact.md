# Fix Bluetooth Screen Crash

The app crashes when the "Disconnected" button (a `TextView` with ID `connStatusTextView`) is clicked in the Bluetooth screen. This is due to the `android:onClick="toggleButtonScan"` attribute in `res/layout/bluetooth.xml` trying to find the `toggleButtonScan` method in the host `Activity` (`MainActivity`), but the method is defined in the `Fragment` (`BluetoothSetUp`).

## Proposed Changes

### UI Layer

#### [MODIFY] [bluetooth.xml](file:///Users/macbook/Documents/GitHub/mdp_group_14_clone/app/src/main/res/layout/bluetooth.xml)
- Remove `android:onClick="toggleButtonScan"` from `connStatusTextView`.

#### [MODIFY] [BluetoothSetUp.java](file:///Users/macbook/Documents/GitHub/mdp_group_14_clone/app/src/main/java/com/example/mdp_group_14/BluetoothSetUp.java)
- Set the click listener for `connStatusTextView` programmatically in `onCreateView`.
- Refactor broadcast receiver registration to avoid multiple registrations in `Scanning()`.
- Fix `onPause` and `onDestroy` to avoid redundant unregistration of broadcast receivers.

### Navigation (Optional but recommended)

#### [MODIFY] [Home.java](file:///Users/macbook/Documents/GitHub/mdp_group_14_clone/app/src/main/java/com/example/mdp_group_14/Home.java)
- Fix the `bluetoothButton` click listener to switch to the Bluetooth tab in the `ViewPager` instead of trying to start `BluetoothSetUp` as an `Activity`, which is invalid since it extends `Fragment`.

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors.

### Manual Verification
1.  Deploy the app to an emulator or device.
2.  Navigate to the Bluetooth screen.
3.  Tap the "Disconnected" button.
4.  Verify that the app no longer crashes and instead initiates a scan.
5.  Verify that the Bluetooth icon in the toolbar (if applicable) correctly navigates to the Bluetooth screen.
