package com.zhihu.matisse.internal.utils;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;

import com.soundcloud.android.crop.Crop;

import java.io.File;

/**
 * Created by chengjuechao on 2017/9/14.
 */

public class CropUtils {

    public static String CropPhoto(String inputPath, Activity activity, int requestCode) {
        File takePhotoImg = new File(activity.getExternalCacheDir(), "crop" + System.currentTimeMillis() + ".jpg");
        Uri imgUri;
        if (Build.VERSION.SDK_INT >= 24) {
            imgUri = FileProvider.getUriForFile(activity, "com.souguo.team.fileprovider", takePhotoImg);
        } else {
            imgUri = Uri.fromFile(takePhotoImg);
        }
        Crop.of(Uri.parse("file://" + inputPath), imgUri).asSquare().start(activity, requestCode);
        return takePhotoImg.getPath();
    }


}
