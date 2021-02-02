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
### What's New -- Version 2.0
- **Support for color and speed controls in patterns.** - if a pattern includes hsv or rgb 
color controls, the driver will detect them and will allow you adjust the pattern using
the Hub's normal color bulb controls.  Similarly if the pattern includes a "Speed" slider,
the driver will allow you to control the pattern's speed via the "SetEffectSpeed" command.  Note
that not all patterns have color/speed controls.  

- **Greatly Improved Multisegment support** -- You no longer need to modify the pattern code to
make your segment settings persistent.  Multisegment settings are now saved on the hub, and
are loaded automatically when you load the new multisegmentforautomation pattern on your Pixelblaze. You can 
adjust the number and size of segments (up to 12) from the driver. See the [multisegment setup guide](https://github.com/zranger1/PixelblazePatterns/blob/master/MULTISEG_HA.md)
for details. 

- **Updated sequencer functionality** - supports the new "shuffle" and "playlist" sequencer modes
available in the latest Pixelblaze firmware.

- **Many performance & reliability improvements** 
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

Click the new device on the Hubitat's "Devices" page, and enter your Pixelblaze's
IP address (the port number is no longer required, but can optionally still be entered) in the provided field.
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



