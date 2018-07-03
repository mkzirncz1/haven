/*
 * Copyright (c) 2013-2015 Marco Ziccardi, Luca Bonato
 * Licensed under the MIT license.
 */


package org.havenapp.main.sensors.motion;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;
import android.support.v8.renderscript.RenderScript;
import android.util.Log;

import com.google.android.cameraview.CameraView;

import org.havenapp.main.sensors.media.ImageCodec;
import org.havenapp.main.sensors.motion.IMotionDetector;
import org.havenapp.main.sensors.motion.LuminanceMotionDetector;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.silvaren.easyrs.tools.Nv21Image;

/**
 * Task doing all image processing in backgrounds, 
 * has a collection of listeners to notify in after having processed
 * the image
 * @author marco
 *
 */
public class MotionDetector {
	
	// Input data
	
	private List<MotionListener> listeners = new ArrayList<>();
	private byte[] rawOldPic;
	private byte[] rawNewPic;
	private int width;
	private int height;
	private Handler handler;
	private int motionSensitivity;
	private int rotationDegrees;
	private int cameraFacing;
	// Output data

	private boolean hasChanged;

	private IMotionDetector detector;

	private RenderScript renderScript;


	public interface MotionListener {
		public void onProcess(Bitmap oldBitmap,
				Bitmap newBitmap,
							  Bitmap rawBitmap,
				boolean motionDetected);
	}
	
	public void addListener(MotionListener listener) {
		listeners.add(listener);
	}
	
	public MotionDetector(
            RenderScript renderScript,
			byte[] rawOldPic, 
			byte[] rawNewPic, 
			int width, 
			int height,
			int rotationDegrees,
			int cameraFacing,
			Handler updateHandler,
			int motionSensitivity) {
	    this.renderScript = renderScript;
		this.rawOldPic = rawOldPic;
		this.rawNewPic = rawNewPic;
		this.width = width;
		this.height = height;
		this.rotationDegrees = rotationDegrees;
		this.cameraFacing = cameraFacing;
		this.handler = updateHandler;
		this.motionSensitivity = motionSensitivity;
        detector = new LuminanceMotionDetector();

    }

	public void setMotionSensitivity (int motionSensitivity)
	{
		this.motionSensitivity = motionSensitivity;
		detector.setThreshold(motionSensitivity);
	}

	public void detect() {
		int[] newPicLuma = ImageCodec.N21toLuma(rawNewPic, width, height);
		if (rawOldPic != null) {

		    int[] oldPicLuma = ImageCodec.N21toLuma(rawOldPic, width, height);
			detector.setThreshold(motionSensitivity);
			List<Integer> changedPixels =
					detector.detectMotion(oldPicLuma, newPicLuma, width, height);
			hasChanged = false;

			int[] newPic = ImageCodec.lumaToGreyscale(newPicLuma, width, height);

			if (changedPixels != null) {
				hasChanged = true;

            }


			if (hasChanged) {


                Bitmap lastBitmap = ImageCodec.lumaToBitmapGreyscale(oldPicLuma, width, height);

                for (int i = 0; i < newPic.length; i++)
                    newPic[i] = Color.TRANSPARENT;

                for (int changedPixel : changedPixels) {
                    newPic[changedPixel] = Color.YELLOW;
                }


                Matrix mtx = new Matrix();

                if (cameraFacing == CameraView.FACING_FRONT) {
                    mtx.postRotate(-90);
                    mtx.postScale(-1, 1, width / 2, height / 2);
                }
                else
                    mtx.postRotate(90);


                Bitmap newBitmap
                        = Bitmap.createBitmap(Bitmap.createBitmap(newPic, width, height, Bitmap.Config.ARGB_4444), 0, 0, width, height, mtx, true);

                Bitmap rawBitmap = Bitmap.createBitmap(Nv21Image.nv21ToBitmap(renderScript, rawNewPic, width, height),0,0,width,height,mtx,true);

                handler.post(() -> {
                    for (MotionListener listener : listeners) {
                        Log.i("MotionDetector", "Updating back view");
                        listener.onProcess(
                                lastBitmap,
                                newBitmap,
                                rawBitmap,
                                hasChanged);
                    }

                });
			}

		}


	}


}