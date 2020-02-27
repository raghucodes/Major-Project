package com.example.android.agsample.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.agsample.Const;
import com.example.android.agsample.R;
import com.example.android.agsample.models.Post;
import com.example.android.agsample.models.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import pub.devrel.easypermissions.AfterPermissionGranted;

import static android.os.Build.VERSION_CODES.KITKAT;
public class NewPostActivity extends BaseActivity {


   // public static final String USERS = "users";
    //public static final String POSTS = "posts";
    private static final int RC_CHOOSE_VIDEO = 111;
    private static final int RC_STORAGE_PERMS = 112;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 222;
    private static final int VIDEO_QUALITY_HIGH = 1;
    private static final String MEDIA = "media";
    private static final String REQUIRED = "Required";
    private static final String KEY_FILE_URI = "key_file_uri";
    private static final String KEY_DOWNLOAD_URL = "key_download_url";
    private static final String APP_PATH_PACKAGE = "com.example.android.agsample";
    private static final String CHILD_FOLDER_NAME = "AgAcademy";
    private static final String VIDEO_NAME_PREFIX = "VID_";
    private static final String FILE_EXTENSION = ".mp4";
    private static final String POSTS_SLASH = "/posts/";
    private static final String USER_POSTS_SLASH = "/user-posts/";
    private static final String SLASH = "/";
    private static final String INTENT_TYPE_VIDEO = "video/*";

    private DatabaseReference mDatabase;
    private EditText mTitleField;
    private EditText mBodyField;
    private FirebaseAuth mAuth;
   // private Task<Uri> mDownloadUrl = null;
    private Uri mDownloadUrl = null;
    private Uri mFileUri = null;
    private StorageReference mStorageRef;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_post);


        initializeFirebase();

        findViews();
        // Restore instance state
        if (savedInstanceState != null) {
            mFileUri = savedInstanceState.getParcelable(KEY_FILE_URI);
            mDownloadUrl = savedInstanceState.getParcelable(KEY_DOWNLOAD_URL);
        }
        uploadVideo();

    }

    private void findViews() {
        mTitleField = (EditText) findViewById(R.id.field_post_title);
        mBodyField = (EditText) findViewById(R.id.field_post_link);
        findViewById(R.id.fab_submit_post).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitPost();
            }
        });
    }

    private void initializeFirebase() {
        mAuth = getFirebaseAuth();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorageRef = FirebaseStorage.getInstance().getReference();
    }


    private void uploadFromUri(Uri fileUri) {
        grantUriPermission(APP_PATH_PACKAGE, fileUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Get a reference to store file at photos/<FILENAME>.jpg
        final StorageReference photoRef = mStorageRef.child(MEDIA)
                .child(fileUri.getLastPathSegment());
        // Upload file to Firebase Storage
        showProgressDialog();
        photoRef.putFile(fileUri)
                .addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        if(taskSnapshot != null) {
                            mDownloadUrl = taskSnapshot.getMetadata().getDownloadUrl();
                           // mDownloadUrl = taskSnapshot.getMetadata().getReference().getDownloadUrl();
                            mBodyField.setText(mDownloadUrl.toString());
                        }
                        hideProgressDialog();
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        mDownloadUrl = null;
                        hideProgressDialog();
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.error_toast_upload_failed),
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        updateUI(mAuth.getCurrentUser());
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelable(KEY_FILE_URI, mFileUri);
        out.putParcelable(KEY_DOWNLOAD_URL, mDownloadUrl);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(data != null) mFileUri = data.getData();
        if (requestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            uploadFromUri(mFileUri);
        } else if (requestCode == RC_CHOOSE_VIDEO && resultCode == RESULT_OK) {
            uploadFromUri(mFileUri);
        } else {
            Toast.makeText(this,
                    getString(R.string.error_toast_taking_vid_failed),
                    Toast.LENGTH_SHORT).show();
        }
    }



    private void uploadVideo() {
        final CharSequence[] items = {
                getString(R.string.take_video_from_camera),
                getString(R.string.choose_video_from_gallery),
                getString(R.string.dialog_choose_cancel)};
        AlertDialog.Builder builder = new AlertDialog.Builder(NewPostActivity.this);
        builder.setTitle(R.string.add_new_video);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (items[item].equals(getString(R.string.take_video_from_camera))) {
                    getCameraVideo();
                } else if (items[item].equals(getString(R.string.choose_video_from_gallery))) {
                    getGalleryVideo();
                } else if (items[item].equals(getString(R.string.dialog_choose_cancel))) {
                    dialog.dismiss();
                    finish();
                }
            }
        });
        builder.setCancelable(false);
        builder.show();
    }




    @AfterPermissionGranted(RC_STORAGE_PERMS)
    private void getCameraVideo() {
        // create new Intentwith with Standard Intent action that can be
        // sent to have the camera application capture an video and return it.
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        // create a file to save the video
        mFileUri = Uri.fromFile(getOutputMediaFile(MEDIA_TYPE_VIDEO));
        // set the image file name
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri);
        // set the video image quality to high
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, VIDEO_QUALITY_HIGH);
        startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // Check that the SDCard is mounted
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), CHILD_FOLDER_NAME);
        // Create the storage directory(SocialAppVideo) if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        // Create a media file name
        File mediaFile;
        if(type == MEDIA_TYPE_VIDEO) {
            // For unique video file name appending current timeStamp with file name
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    VIDEO_NAME_PREFIX + UUID.randomUUID().toString() + FILE_EXTENSION);
        } else {
            return null;
        }
        return mediaFile;
    }

    private void getGalleryVideo() {
        if (Build.VERSION.SDK_INT < KITKAT) {
            Intent takeVideoIntent = new Intent(Intent.ACTION_PICK);
            takeVideoIntent.setType(INTENT_TYPE_VIDEO);
            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri);
            startActivityForResult(takeVideoIntent, RC_CHOOSE_VIDEO);
        } else {
            Intent takeVideoIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            takeVideoIntent.addCategory(Intent.CATEGORY_OPENABLE);
            takeVideoIntent.setType(INTENT_TYPE_VIDEO);
            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri);
            startActivityForResult(takeVideoIntent, RC_CHOOSE_VIDEO);
        }
    }


    private void updateUI(FirebaseUser user) {
        if (user != null && mDownloadUrl != null) {
            ((TextView) findViewById(R.id.field_post_link)).setText(mDownloadUrl.toString());
        } else {
            ((TextView) findViewById(R.id.field_post_link)).setText(null);
        }
    }

    private void submitPost() {
        final String title = mTitleField.getText().toString();
        final String body = mBodyField.getText().toString();
        // Title is required
        if (TextUtils.isEmpty(title)) {
            mTitleField.setError(REQUIRED);
            return;
        }
        // Body is required
        if (TextUtils.isEmpty(body)) {
            mBodyField.setError(REQUIRED);
            return;
        }
        // single_value_read]
        final String userId = getUid();
        mDatabase.child(Const.USERS).child(userId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // Get user value
                        User user = dataSnapshot.getValue(User.class);
                        if (user == null) {
                            // User is null, error out
                            Toast.makeText(NewPostActivity.this,
                                    getString(R.string.error_toast_not_fetch_user),
                                    Toast.LENGTH_SHORT).show();
                            //writeNewPost(userId, user.username, title, body);
                        } else {
                            // Write new post
                            writeNewPost(userId, user.username, title, body);
                        }
                        // Finish this Activity, back to the stream
                        finish();
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        String errorMsg = databaseError.getMessage();
                        Toast.makeText(NewPostActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void writeNewPost(String userId, String username, String title, String body) {
        // Create new post at /user-posts/$userid/$postid and at
        // /posts/$postid simultaneously
        String key = mDatabase.child(Const.POSTS).push().getKey();
        Post post = new Post(userId, username, title, body);
        Map<String, Object> postValues = post.toMap();
        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put(POSTS_SLASH + key, postValues);
        childUpdates.put(USER_POSTS_SLASH + userId + SLASH + key, postValues);
        mDatabase.updateChildren(childUpdates);
    }
}
