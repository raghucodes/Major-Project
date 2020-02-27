package com.example.android.agsample.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.agsample.AboutUs;
import com.example.android.agsample.Const;
import com.example.android.agsample.R;
import com.example.android.agsample.models.Post;
import com.example.android.agsample.viewholder.PostViewHolder;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;


public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {


    public static final int POSTS_QUERY_LIMIT = 100;
    private static final int ONE_LIKE = 1;
    private static final String USER_POSTS = "user-posts";
    private static final String SHARE_INTENT_TYPE = "text/plain";
    private static final long HANDLER_DELAY = 5000;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseRecyclerAdapter<Post, PostViewHolder> mAdapter;
    private RecyclerView mPostsList;
    boolean isPopulateViewHolderCalled;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        showProgressDialog();
        initializeFirebase();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                       // .setAction("Action", null).show();

                startActivity(new Intent(MainActivity.this, NewPostActivity.class));
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        View headerView = navigationView.getHeaderView(0);
        TextView navUsername = (TextView) headerView.findViewById(R.id.navUserName);
        navUsername.setText(RegisterActivity.name);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user.getEmail();
        TextView navUserMail = (TextView) headerView.findViewById(R.id.navUserMail);
        navUserMail.setText(email);

        mAuth = getFirebaseAuth();

        showPosts();
    }

    private void showPosts() {
        setUpRecyclerView();
        // Set up FirebaseRecyclerAdapter with the Query
        Query postsQuery = getQuery(mDatabase);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hideProgressDialog();
                if( !isPopulateViewHolderCalled) {
                    findViewById(R.id.empty_list_layout).setVisibility(View.VISIBLE);
                }
            }
        }, HANDLER_DELAY);
        mAdapter = new FirebaseRecyclerAdapter<Post, PostViewHolder>(Post.class, R.layout.include_post,
                PostViewHolder.class, postsQuery) {
            @Override
            protected void populateViewHolder(final PostViewHolder viewHolder, final Post model, final int position) {
                final DatabaseReference postRef = getRef(position);
                // Set click listener for the whole post view
                final String postKey = postRef.getKey();
                viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Launch PostDetailActivity
                        Intent intent = new Intent(getApplicationContext(), PostDetailActivity.class);
                        intent.putExtra(PostDetailActivity.EXTRA_POST_KEY, postKey);
                        startActivity(intent);
                    }
                });

                // Determine if the current user has liked this post and set UI accordingly
                if (model.likes.containsKey(getUid())) {
                    viewHolder.likesView.setImageResource(R.drawable.ic_star_black_24dp);
                } else {
                    viewHolder.likesView.setImageResource(R.drawable.ic_star_border_black_24dp);
                }

                // Bind Post to ViewHolder, setting OnClickListener for the like button
                viewHolder.bindToPost(model, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int id = view.getId();
                        if(id == R.id.post_like) {
                            // Need to write to both places the post is stored
                            DatabaseReference globalPostRef = mDatabase.child(Const.POSTS).child(postRef.getKey());
                            DatabaseReference userPostRef = mDatabase.child(USER_POSTS).child(model.uid).child(postRef.getKey());

                            // Run two transactions
                            onLikeClicked(globalPostRef);
                            onLikeClicked(userPostRef);
                        } else if(id == R.id.post_share) {
                            onShareClicked(model.body);
                        }
                    }
                });

                isPopulateViewHolderCalled = true;
                hideProgressDialog();
                findViewById(R.id.empty_list_layout).setVisibility(View.GONE);
                findViewById(R.id.list_layout).setVisibility(View.VISIBLE);
            }
        };

        mPostsList.setAdapter(mAdapter);

    }


    private void setUpRecyclerView() {
        mPostsList = (RecyclerView) findViewById(R.id.main_posts_list);
        mPostsList.setHasFixedSize(true);
        mPostsList.setLayoutManager(getLinLayoutManager());
    }

    private LinearLayoutManager getLinLayoutManager() {
        // Set up Layout Manager, reverse layout
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setReverseLayout(true);
        manager.setStackFromEnd(true);
        return manager;
    }

    private void onLikeClicked(DatabaseReference postRef) {
        postRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                Post p = mutableData.getValue(Post.class);
                if (p == null) {
                    return Transaction.success(mutableData);
                }
                if (p.likes.containsKey(getUid())) {
                    // unlike the post and remove self from stars
                    p.likesCount = p.likesCount - ONE_LIKE;
                    p.likes.remove(getUid());
                } else {
                    // like the post and add self to stars
                    p.likesCount = p.likesCount + ONE_LIKE;
                    p.likes.put(getUid(), true);
                }
                // Set value and report transaction success
                mutableData.setValue(p);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean b,
                                   DataSnapshot dataSnapshot) {
                // Transaction completed
            }
        });
    }

    private void onShareClicked(String videoLink) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(SHARE_INTENT_TYPE);
        shareIntent.putExtra(Intent.EXTRA_TEXT, videoLink);
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_intent_subject));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_intent_title)));
    }

    public String getUid() {
        if(mAuth.getCurrentUser() != null) {
            return mAuth.getCurrentUser().getUid();
        }
        return null;
    }

    public Query getQuery(DatabaseReference databaseReference) {
        // Last 100 posts, these are automatically the 100 most recent
        return databaseReference.child(Const.POSTS)
                .limitToFirst(POSTS_QUERY_LIMIT);
    }

    private void initializeFirebase() {
        //get firebase auth instance
        mAuth = getFirebaseAuth();
        //get current user
        final FirebaseUser user = mAuth.getCurrentUser();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    // user auth state is changed - user is null
                    // launch login activity
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }
            }
        };
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }


    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAdapter != null) {
            mAdapter.cleanup();
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            showInfoDialog(mAuth.getCurrentUser());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_create) {
            startActivity(new Intent(MainActivity.this,NewPostActivity.class));
            // Handle the camera action
        } else if (id == R.id.nav_phone) {
            String phone = "9963420626";
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null));
            startActivity(intent);


        } else if (id == R.id.nav_chatbot) {

        } else if (id == R.id.nav_about) {

            Intent intent = new Intent(MainActivity.this, AboutUs.class);
            startActivity(intent);

        } else if (id == R.id.nav_report) {
            try {
                Intent intent = new Intent (Intent.ACTION_VIEW , Uri.parse("mailto:" + "raghuvardhan609@gmail.com"));
                intent.putExtra(Intent.EXTRA_SUBJECT, "reporting a problem");
                intent.putExtra(Intent.EXTRA_TEXT, "this has been a very great issue .Hope you will resolve this ASAP");
                startActivity(intent);
            } catch(Exception e) {
                Toast.makeText(getBaseContext(), "Sorry...You don't have any mail app", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

        } else if (id == R.id.nav_signout) {
           /* AuthUI.getInstance()
                    .signOut(this)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        public void onComplete(@NonNull Task<Void> task) {
                            // user is now signed out
                            startActivity(new Intent(MainActivity.this, LoginActivity.class));
                            finish();
                        }
                    });*/
           mAuth.signOut();
           startActivity(new Intent(MainActivity.this,LoginActivity.class));
           finish();

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
    private void showInfoDialog(FirebaseUser user) {
        String userEmailText = getString(R.string.user_email_text)+ user.getEmail();
        String userIdText = getString(R.string.user_id_text)+ user.getUid();
        String userInfo = userEmailText + "\n" + userIdText;
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_info_title)
                .setMessage(userInfo)
                .create()
                .show();
    }
}
