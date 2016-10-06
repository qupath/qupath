#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Icons were made with the help of ImageMagick

echo $DIR

mkdir $DIR/qupath.iconset

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 16x16 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_16x16.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 32x32 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_32x32.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 128x128 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_128x128.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 256x256 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_256x256.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 512x512 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_512x512.png
          
convert $DIR/QuPath.png  \
          \( -clone 0 -resize 32x32 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_16x16@2x.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 64x64 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_32x32@2x.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 256x256 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_128x128@2x.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 512x512 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_256x256@2x.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 1024x1024 \) \
          -delete 0 -alpha on $DIR/qupath.iconset/icon_512x512@2x.png

iconutil -c icns qupath.iconset


# Create Windows ico file as well

convert $DIR/qupath.iconset/icon_128x128.png  \
      \( -clone 0 -resize 16x16 \) \
      \( -clone 0 -resize 32x32 \) \
      \( -clone 0 -resize 48x48 \) \
      \( -clone 0 -resize 64x64 \) \
      QuPath.ico
      
convert $DIR/qupath.iconset/icon_128x128.png  \
      \( -clone 0 -resize 48x48 \) \
      -delete 0 QuPath-setup-icon.bmp


# Also create some Icons for Java

mkdir $DIR/JavaFX

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 16x16 \) \
          -delete 0 -alpha on $DIR/JavaFX/QuPath_16.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 32x32 \) \
          -delete 0 -alpha on $DIR/JavaFX/QuPath_32.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 48x48 \) \
          -delete 0 -alpha on $DIR/JavaFX/QuPath_48.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 64x64 \) \
          -delete 0 -alpha on $DIR/JavaFX/QuPath_64.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 128x128 \) \
          -delete 0 -alpha on $DIR/JavaFX/QuPath_128.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 256x256 \) \
          -delete 0 -alpha on $DIR/JavaFX/QuPath_256.png

convert $DIR/QuPath.png  \
          \( -clone 0 -resize 512x512 \) \
          -delete 0 -alpha on $DIR/JavaFX/QuPath_512.png
          