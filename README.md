## gaze recognition
+ Stage 1:Facial Landmark Detection


### Features

* Support HOG detector on Facial Landmark Detection

### Demo

[![Demo video](https://gifs.com/gif/O76BEG)]https://www.youtube.com/watch?v=da8KD5rdses&feature=youtu.be)

### Build

#### Android app
* Open Android studio to build

* Use command line to build (Optional)

On Windows platforms, type this command:

`$ gradlew.bat assembleDebug`

On Mac OS and Linux platforms, type these commands:

```
$ ./gradlew assembleDebug

or

$ make ; make install

```

### Try directly

Install the apk

`$ adb install demo/app-debug.apk`

Otherwise, import the library to your build.gradle

```
repositories {
    maven {
        url 'https://dl.bintray.com/tzutalin/maven'
    }
}

dependencies {
    compile 'com.tzutalin.dlib-android-app:dlib:1.0.3'
}

```

### Sample code

Facial landmark detection
```java
PeopleDet peopleDet = new PeopleDet();
List<VisionDetRet> results = peopleDet.detBitmapFace(bitmap, Constants.getFaceShapeModelPath());
for (final VisionDetRet ret : results) {
    String label = ret.getLabel(); // If doing face detection, it will be 'Face'
    int rectLeft = ret.getLeft();
    int rectTop= ret.getTop();
    int rectRight = ret.getRight();
    int rectBottom = ret.getBottom();
    ArrayList<Point> landmarks = ret.getFaceLandmarks();
    for (Point point : landmarks) {
        int pointX = (int) (point.x * resizeRatio);
        int pointY = (int) (point.y * resizeRatio);
        // Get the point of the face landmarks
    }
}
```

### License
[License](LICENSE.md)
