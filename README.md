# hubitatpixelblazedriver 
Hubitat Elevation device handler for use with the Pixelblaze addressable LED controller.

For information on Pixelblaze, visit its home: [www.electromage.com](www.electromage.com). And check out 
the Pixelblaze forums at [www.forum.electromage.com](https://forum.electromage.com/) .

Pixelblaze is a programmable LED controller with a fast Javascript-like interpreter built in for easy no compile/no upload
programmability. It supports most common types of addressable LEDs and has an easy-to-use web-based UI, including a built-in IDE!
## What can I do with this driver?
This driver gives the Hubitat control over the Pixelblaze - on/off, brightness,
color, pattern (effects) management, etc.  It also gives you the ability, with the
help of an optional pattern (available here) to divide your LED strip into multiple
segments, each acting as an independent color bulb device with its own settings and effects.

It works with RuleMachine and the Maker API, so you can 
coordinate the Pixelblaze with your other home automation devices.

### What's New -- Version 2.0.5
You can now use the the Pixelblaze discovery service to get IP addresses for Pixelblaze devices by name on your LAN.  Static IP addresses are no longer required.
To use this feature:
- on the "Settings" tab of your Pixelblaze's web UI, enable Network Discovery
- enable **Use Pixelblaze discovery service to find IP address** in the Preferences section of your device
page on your hub.
- set the Pixelblaze's "Device Name" on your hub to be the same as the name shown on the Settings tab of the web UI.
- save your device preferences on the hub

Your hub will now be able to find your Pixelblaze, even if its IP address changes.

### What's New -- Version 2.0.4
**Minimizing flash RAM writes to extend Pixelblaze lifespan** - the Pixelblaze is based on the Espressif ESP32, which has 
flash RAM rated for 100,000 cycles.  This is actually... not a lot for a device that is focused on programmability. Depending on how you use your Pixelblaze, it's possible to reach the limit in a finite amount
of time and possibly "wear out" the device's memory.  This driver update focuses on minimizing the number of flash writes to reduce
wear and tear.

Settings to control this behavior are now on the "Preferences" section of the driver page.

This change will affect how the Pixelblaze behaves after being power cycled.  Previously it always saved level and pattern settings almost immediately and if rebooted, would return to the last state set by either the Hubitat or by the Web UI.

The new default behavior is to disable all writes to flash from the Hubitat.  This means that on rebooting, the Pixelblaze will revert to the last pattern/brightness settings made via the Pixelblaze web UI.
 
You can control this behavior with the **Allow settings to persist through power loss** switch in Preferences. It is **off** by default which, as mentioned, disables all flash writes.  Turning it **on** causes updated brightness and pattern settings to be saved only after the controls have been stable for some period of time.

The wait period defaults to 8 seconds, and can be configured by entering a new value, in seconds, in the **Time (seconds) to wait after last control change before saving light settings** 
field in Preferences.

My oldest Pixelblaze has been in daily use since 2019 and shows no signs of quitting - this is not an issue that will affect most people for a very long time.  Still, it's worth making trying to ensure that all Pixelblazes lead long, productive and happy lives.
 
## To Install
Set up your Pixelblaze.  Make note of its IP address.
#### Install the Driver 
Search for "Pixelblaze" in the Hubitat Package manager and let the package manager install
the driver.  This is the preferred method as you will automatically be notified when an 
update is available.

-- or if you prefer to install the driver manually --

On your Hub's admin page, select "Drivers Code", then click the
"New Driver" button.  Paste the Groovy driver code for both the parent driver 
(HubitatPixelblazeDriver.groovy) and the child driver (PixelblazeSegmentChild.groovy) 
from this repository into the editor window and click the "SAVE" button.
#### Create and Configure a Device on your Hub
Create a new virtual device on your Hubitat, name and label it, and select 
"Pixelblaze Controller" as the device type.  Save your new device.

Entering a static IP address is no longer required.  If you do wish use a static IP address, click the new device on the Hubitat's "Devices" page, and enter your Pixelblaze's IP address (the port number is no longer required, but can optionally still be entered) in the provided field.  

To have the hub find your Pixelblaze by name:
- on the "Settings" tab of your Pixelblaze's web UI, enable Network Discovery
- enable **Use Pixelblaze discovery service to find IP address** in the Preferences section of your device
page on your hub.
- set the Pixelblaze's "Device Name" on your hub to be the same as the name shown on the Settings tab of the web UI.

Press the "Save Preferences" button, and your Pixelblaze hub device will be ready to use.


#### Legacy on/off switching method 
*The ability to switch on/off by selecting patterns has been preserved for backward
compatibiliby. It is now disabled by default when the driver is installed.*

If you wish to use On and Off patterns, you will need to enter the names of the patterns to use for the "On" and "Off"
states. You can use any pattern stored on your Pixelblaze for either state.

To set all pixels dark with an "Off" pattern, use a pattern that does
nothing when called to render pixels.  For example:
```javascript
   export function render(index) {
     ; // do absolutely nothing
   }
```

## Donation
If this project saves you time and effort, please consider donating to help support further development.  Every donut or cup of coffee helps!  :-)

[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/donate/?hosted_button_id=YM9DKUT5V34G8)



