## What is it?

App for transmitting location data to sony cameras. I was fed up with the official app and found a python script (https://github.com/anoulis/sony_camera_bluetooth_external_gps) that got the protocol right, so I thought let's make a better app.

Also big thanks to https://github.com/mlapaglia/AlphaSync which provided insights into how to enable the functionality for devices that use the Sony creators's app instead of Imaging Edge.

It makes use of the companion device manager APIs of Android which *should* provide a reliable way to launch the transmission even when the app is in the background and the screen is off. However, due to limited devices and emulators not providing bluetooth access I couldn't test it with many android versions (13, 15 and 16 up to now).

I also only have access to an Alpha 6400, but it should probably work with most of the 6000 series devices (?). I'm also not a very good android dev, but it supports dark mode (yay)


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




<img width="242" height="512" alt="unnamed (1)" src="https://github.com/user-attachments/assets/0ee0e403-70a1-431c-bb68-99ddf03b95c3" />

<img width="242" height="512" alt="unnamed (3)" src="https://github.com/user-attachments/assets/7acf30f5-3bbd-4d66-a5c0-bd779c47ccc8" />
<img width="242" height="512" alt="unnamed (4)" src="https://github.com/user-attachments/assets/60c69256-10be-4422-8864-525b146d174f" />
