## What is it?

App for transmitting location data to sony cameras. I was fed up with the official app and found a python script () that got the protocol right, so I thought let's make a better app.

It makes use of the companion device manager APIs of Android which *should* provide a reliable way to launch the transmission even when the app is in the background and the screen is off. However, due to limited devices and emulators not providing bluetooth access I couldn't test it with many android versions (15 and 16 up to now).

I also only have access to an Alpha 6400, but it should probably work with most of the 6000 series devices (?).


## How does it work?

### Associate the device
- Select Start and follow the instructions. Make sure bluetooth is turned on and the camera is in coupling mode (if it was not coupled before already, then this step is not neccesary). The app should prompt you to bond the device if needed.
- After selecting the device, it should appear on the list of associated devices and will start transmitting the location data.
- When turning off the camera, the transmission will stop. Once android realizes the device is gone, the service will also be destroyed, releasing any resources the app uses.
- Check the logs if any issues are found and post them here.
