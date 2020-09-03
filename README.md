# EscapeWildFire

## About the *EscapeWildFire* framework

Wildfires have a great impact on a considerable number of countries around the world, costing thousands of lives yearly and inflicting large societal and economical problems in the affected region. Being able to escape wildfires is often difficult and dangerous without prior knowledge of the fire propagation and the possible escape routes. The availability of modern technologies, such as smartphones, could significantly improve the provision of real-time information to ordinary citizens in order to help them to evacuate as soon as possible. The goal of *Escape Wildfires* is therefore to provide a framework for fire departments and governing bodies where they can enter information about wildfire observations, model the future spread of the fires and communicate escape routes to the public. The framework aims to provide this information with high precision and efficiency, based on the predicted spread of the wildfire and the real-time location of the end user.

![System architecture diagram](SystemArchitecture.png)

## Screenshots
### Mobile application
![Screenshots of the mobile app](AndroidAppScreenshots.png)

### Fire management tool
![Screenshots of the web app](FireManagementScreenshots.png)

## How to run EscapeWildFire?
1.  Get authorization keys from [Windy API](https://api.windy.com/) and [HERE XYZ](https://www.here.xyz/).
2.  Paste keys into the *simulateWildfire.py* files.
3.  Execute the *python3 webApplication.py* command to run the main program.
4.  Open the Android Studio project and set authorization variable to your personal HERE XYZ key.
5.  Build and run the mobile app using the Android Emulator (in Android Studio) or an Android phone.
6.  Open *localhost:5000* in the browser.
7.  Start managing wildfires in the browser and observe the mobile application
