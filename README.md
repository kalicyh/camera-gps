## What is it?

App for transmitting location data to sony cameras. I was fed up with the official app and found a python script (https://github.com/anoulis/sony_camera_bluetooth_external_gps) that got the protocol right, so I thought let's make a better app.

Also big thanks to https://github.com/mlapaglia/AlphaSync which provided insights into how to enable the functionality for devices that use the Sony creators's app instead of Imaging Edge.

It makes use of the companion device manager APIs of Android which *should* provide a reliable way to launch the transmission even when the app is in the background and the screen is off. However, due to limited devices, there might be differences. I have tested Android 10, 12, 13, 15 and 16 up to now, but your mileage may wary


## How to install?


<a href="https://play.google.com/store/apps/details?id=com.saschl.cameragps"><img width="270" height="80" alt="GetItOnGooglePlay_Badge_Web_color_English" src="https://github.com/user-attachments/assets/775cb6fc-a297-4208-9249-43291c52d045" /></a>

OR

 Get the APK from the releases and install it directly on your phone. Or use Obtainium and enter the repo URL for a more seamless experience.
  

## How does it work?

### Associate the device
- Make sure you grant the app the needed permissions. Background location is important so that the app runs properly when the phone is not in use.
- Select Start and follow the instructions. Make sure bluetooth is turned on and the camera is in coupling mode (if it was not coupled before already, then this step is not neccesary). The app should prompt you to bond the device if needed.
- After selecting the device, it should appear on the list of associated devices and will start transmitting the location data.
- When turning off the camera, the transmission will stop. Once android realizes the device is gone, the service will also be destroyed, releasing any resources the app uses.
- Check the logs if any issues are found and post them here.

## It doesn't work!

As always with Android, not all devices are created equal. Different manufacturers might implement Android features differently or even produce bugs while doing the customizations. For troubleshooting you can try the following steps.

- Turn off the camera - wait one minute - turn it on again
- Remove the association and try again (tap on the device on the main screen to get to this option)
- Check if any battery optimizations are active (those shouldn't hurt the app, but one never knows). Also see https://dontkillmyapp.com for instructions
- Use the "Always On" mode. In the device details screen (accessible by tapping the device name on the main screen), enable the "Always on" toggle. This will start a Foreground Service which will stay active as long as the option is enabled. This might potentially use more battery, but I have optimized it that it more or less idles when the camera is not connected. That means:
   - No location data will be accessed
   - No transmission of data is performed
until the device is connected again (this will happen automatically once the camera is turned on again, no action required from your side!

### Still no luck?
 - Feel free to open an issue here on Github and paste the logs if possible (accessible from the main screen by clicking on the icon with the three lines). I will take a look then

## Which cameras are supported?

- All cameras that are supported by the Imaging Edge and Creators' app should also be supported. Reports are welcome, as I do not own all cameras ;) A6400 and ZV-E10 are confirmed working.


<img width="242" height="512" alt="unnamed (1)" src="https://github.com/user-attachments/assets/0ee0e403-70a1-431c-bb68-99ddf03b95c3" />

<img width="242" height="512" alt="unnamed (3)" src="https://github.com/user-attachments/assets/7acf30f5-3bbd-4d66-a5c0-bd779c47ccc8" />
<img width="242" height="512" alt="unnamed (4)" src="https://github.com/user-attachments/assets/60c69256-10be-4422-8864-525b146d174f" />
