/*
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zhihu.matisse.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import com.zhihu.matisse.R;
import com.zhihu.matisse.internal.entity.Album;
import com.zhihu.matisse.internal.entity.Item;
import com.zhihu.matisse.internal.entity.SelectionSpec;
import com.zhihu.matisse.internal.model.AlbumCollection;
import com.zhihu.matisse.internal.model.SelectedItemCollection;
import com.zhihu.matisse.internal.ui.AlbumPreviewActivity;
import com.zhihu.matisse.internal.ui.BasePreviewActivity;
import com.zhihu.matisse.internal.ui.MediaSelectionFragment;
import com.zhihu.matisse.internal.ui.SelectedPreviewActivity;
import com.zhihu.matisse.internal.ui.adapter.AlbumMediaAdapter;
import com.zhihu.matisse.internal.ui.adapter.AlbumsAdapter;
import com.zhihu.matisse.internal.ui.widget.AlbumsSpinner;
import com.zhihu.matisse.internal.utils.CompressUtils;
import com.zhihu.matisse.internal.utils.CropUtils;
import com.zhihu.matisse.internal.utils.MediaStoreCompat;
import com.zhihu.matisse.internal.utils.PathUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import top.zibin.luban.OnCompressListener;

import static com.zhihu.matisse.internal.utils.CropUtils.CropPhoto;

/**
 * Main Activity to display albums and media content (images/videos) in each album
 * and also support media selecting operations.
 */
public class MatisseActivity extends AppCompatActivity implements
        AlbumCollection.AlbumCallbacks, AdapterView.OnItemSelectedListener,
        MediaSelectionFragment.SelectionProvider, View.OnClickListener,
        AlbumMediaAdapter.CheckStateListener, AlbumMediaAdapter.OnMediaClickListener,
        AlbumMediaAdapter.OnPhotoCapture {

    public static final String EXTRA_RESULT_SELECTION = "extra_result_selection";
    public static final String EXTRA_RESULT_SELECTION_PATH = "extra_result_selection_path";
    private static final int REQUEST_CODE_PREVIEW = 23;
    private static final int REQUEST_CODE_CAPTURE = 24;
    private static final int REQUEST_CODE_CROP = 25;
    private static final int REQUEST_CODE_PERMISSION = 22;
    private final AlbumCollection mAlbumCollection = new AlbumCollection();
    private MediaStoreCompat mMediaStoreCompat;
    private SelectedItemCollection mSelectedCollection = new SelectedItemCollection(this);
    private SelectionSpec mSpec;

    private AlbumsSpinner mAlbumsSpinner;
    private AlbumsAdapter mAlbumsAdapter;
    private TextView mButtonPreview;
    private TextView mButtonApply;
    private View mContainer;
    private View mEmptyView;
    private ArrayList<String> mPathList = new ArrayList<>();
    private int mSelectNumber;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // programmatically set theme before super.onCreate()
        mSpec = SelectionSpec.getInstance();
        setTheme(mSpec.themeId);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_matisse);

        if (mSpec.needOrientationRestriction()) {
            setRequestedOrientation(mSpec.orientation);
        }

        if (mSpec.capture) {
            mMediaStoreCompat = new MediaStoreCompat(this);
            if (mSpec.captureStrategy == null)
                throw new RuntimeException("Don't forget to set CaptureStrategy.");
            mMediaStoreCompat.setCaptureStrategy(mSpec.captureStrategy);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        Drawable navigationIcon = toolbar.getNavigationIcon();
        TypedArray ta = getTheme().obtainStyledAttributes(new int[]{R.attr.album_element_color});
        int color = ta.getColor(0, 0);
        ta.recycle();
        navigationIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        mButtonPreview = (TextView) findViewById(R.id.button_preview);
        mButtonApply = (TextView) findViewById(R.id.button_apply);
        mButtonPreview.setOnClickListener(this);
        mButtonApply.setOnClickListener(this);
        mContainer = findViewById(R.id.container);
        mEmptyView = findViewById(R.id.empty_view);

        mSelectedCollection.onCreate(savedInstanceState);
        updateBottomToolbar();

        mAlbumsAdapter = new AlbumsAdapter(this, null, false);
        mAlbumsSpinner = new AlbumsSpinner(this);
        mAlbumsSpinner.setOnItemSelectedListener(this);
        mAlbumsSpinner.setSelectedTextView((TextView) findViewById(R.id.selected_album));
        mAlbumsSpinner.setPopupAnchorView(findViewById(R.id.toolbar));
        mAlbumsSpinner.setAdapter(mAlbumsAdapter);
        mAlbumCollection.onCreate(this, this);
        mAlbumCollection.onRestoreInstanceState(savedInstanceState);
        mAlbumCollection.loadAlbums();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mSelectedCollection.onSaveInstanceState(outState);
        mAlbumCollection.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAlbumCollection.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == REQUEST_CODE_PREVIEW) {//预览回调
            Bundle resultBundle = data.getBundleExtra(BasePreviewActivity.EXTRA_RESULT_BUNDLE);
            ArrayList<Item> selected = resultBundle.getParcelableArrayList(SelectedItemCollection.STATE_SELECTION);
            int collectionType = resultBundle.getInt(SelectedItemCollection.STATE_COLLECTION_TYPE,
                    SelectedItemCollection.COLLECTION_UNDEFINED);
            if (data.getBooleanExtra(BasePreviewActivity.EXTRA_RESULT_APPLY, false)) {//预览使用
                //Intent result = new Intent();
                //ArrayList<Uri> selectedUris = new ArrayList<>();
                ArrayList<String> selectedPaths = new ArrayList<>();
                if (selected != null) {
                    for (Item item : selected) {
                        //selectedUris.add(item.getContentUri());
                        selectedPaths.add(PathUtils.getPath(this, item.getContentUri()));
                    }
                    mSelectNumber = selectedPaths.size();
                    if (mSelectNumber == 1 && mSpec.isCrop) {
                        //需要裁剪
                        mPathList.add(CropUtils.CropPhoto(selectedPaths.get(0), MatisseActivity.this, REQUEST_CODE_CROP));
                        return;
                    }
                    try {
                        CompressUtils.CompressPhoto(MatisseActivity.this, selectedPaths, mOnCompressListener);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
                //result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
                //setResult(RESULT_OK, result);
                //finish();
            } else {//预览返回
                mSelectedCollection.overwrite(selected, collectionType);
                Fragment mediaSelectionFragment = getSupportFragmentManager().findFragmentByTag(
                        MediaSelectionFragment.class.getSimpleName());
                if (mediaSelectionFragment instanceof MediaSelectionFragment) {
                    ((MediaSelectionFragment) mediaSelectionFragment).refreshMediaGrid();
                }
                updateBottomToolbar();
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE) {//相机
            // Just pass the data back to previous calling Activity.
            //Uri contentUri = mMediaStoreCompat.getCurrentPhotoUri();
            String path = mMediaStoreCompat.getCurrentPhotoPath();
            //ArrayList<Uri> selected = new ArrayList<>();
            //selected.add(contentUri);
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + path));
            sendBroadcast(intent);
            ArrayList<String> selectedPath = new ArrayList<>();
            selectedPath.add(path);
            mSelectNumber = 1;
            if (mSpec.isCrop) {
                //需要裁剪
                mPathList.add(CropPhoto(path, MatisseActivity.this, REQUEST_CODE_CROP));
                return;
            }
            try {
                CompressUtils.CompressPhoto(MatisseActivity.this, selectedPath, mOnCompressListener);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //Intent result = new Intent();
            //result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selected);
            //result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPath);
            //setResult(RESULT_OK, result);
            //if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            // MatisseActivity.this.revokeUriPermission(contentUri,
            // Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            //finish();
        } else if (requestCode == REQUEST_CODE_CROP) {//裁剪
            String path = mPathList.get(0);
            mPathList.clear();
            try {
                CompressUtils.CompressPhoto(MatisseActivity.this, path, mOnCompressListener);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateBottomToolbar() {
        int selectedCount = mSelectedCollection.count();
        if (selectedCount == 0) {
            mButtonPreview.setEnabled(false);
            mButtonApply.setEnabled(false);
            mButtonApply.setText(getString(R.string.button_apply_default));
        } else if (selectedCount == 1 && mSpec.singleSelectionModeEnabled()) {
            mButtonPreview.setEnabled(true);
            mButtonApply.setText(R.string.button_apply_default);
            mButtonApply.setEnabled(true);
        } else {
            mButtonPreview.setEnabled(true);
            mButtonApply.setEnabled(true);
            mButtonApply.setText(getString(R.string.button_apply, selectedCount));
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_preview) {
            Intent intent = new Intent(this, SelectedPreviewActivity.class);
            intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
            startActivityForResult(intent, REQUEST_CODE_PREVIEW);
        } else if (v.getId() == R.id.button_apply) {
            //Intent result = new Intent();
            //ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
            //result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
            ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection.asListOfString();
            mSelectNumber = selectedPaths.size();
            if (mSelectNumber == 1 && mSpec.isCrop) {
                //需要裁剪
                mPathList.add(CropPhoto(selectedPaths.get(0), MatisseActivity.this, REQUEST_CODE_CROP));
                return;
            }
            try {
                CompressUtils.CompressPhoto(MatisseActivity.this, selectedPaths, mOnCompressListener);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
            //setResult(RESULT_OK, result);
            //finish();
        }
    }

    private OnCompressListener mOnCompressListener = new OnCompressListener() {
        @Override
        public void onStart() {

        }

        @Override
        public void onSuccess(File file) {
            mPathList.add(file.getPath());
            if (mPathList.size() == mSelectNumber) {
                //压缩完成
                Intent result = new Intent();
                result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, mPathList);
                setResult(RESULT_OK, result);
                finish();
            }
        }

        @Override
        public void onError(Throwable e) {
            Toast.makeText(MatisseActivity.this, "图片处理失败,请重试", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    };

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mAlbumCollection.setStateCurrentSelection(position);
        mAlbumsAdapter.getCursor().moveToPosition(position);
        Album album = Album.valueOf(mAlbumsAdapter.getCursor());
        if (album.isAll() && SelectionSpec.getInstance().capture) {
            album.addCaptureCount();
        }
        onAlbumSelected(album);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onAlbumLoad(final Cursor cursor) {
        mAlbumsAdapter.swapCursor(cursor);
        // select default album.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                cursor.moveToPosition(mAlbumCollection.getCurrentSelection());
                mAlbumsSpinner.setSelection(MatisseActivity.this,
                        mAlbumCollection.getCurrentSelection());
                Album album = Album.valueOf(cursor);
                if (album.isAll() && SelectionSpec.getInstance().capture) {
                    album.addCaptureCount();
                }
                onAlbumSelected(album);
            }
        });
    }

    @Override
    public void onAlbumReset() {
        mAlbumsAdapter.swapCursor(null);
    }

    private void onAlbumSelected(Album album) {
        if (album.isAll() && album.isEmpty()) {
            mContainer.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            Fragment fragment = MediaSelectionFragment.newInstance(album);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment, MediaSelectionFragment.class.getSimpleName())
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onUpdate() {
        // notify bottom toolbar that check state changed.
        updateBottomToolbar();
    }

    @Override
    public void onMediaClick(Album album, Item item, int adapterPosition) {
        Intent intent = new Intent(this, AlbumPreviewActivity.class);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ALBUM, album);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ITEM, item);
        intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
        startActivityForResult(intent, REQUEST_CODE_PREVIEW);
    }

    @Override
    public SelectedItemCollection provideSelectedItemCollection() {
        return mSelectedCollection;
    }

    @Override
    public void capture() {
        if (mMediaStoreCompat != null) {
            //动态权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSION);
                    return;
                }
            }
            mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION)
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "没有权限,无法打开相机", Toast.LENGTH_SHORT).show();
            } else if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mMediaStoreCompat.dispatchCaptureIntent(this, REQUEST_CODE_CAPTURE);
            }
    }
}