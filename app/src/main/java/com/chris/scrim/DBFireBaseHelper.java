package com.chris.scrim;

import android.content.Context;
import android.util.Log;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

/**
 * Created by chris on 2/22/2016.
 */
public class DBFireBaseHelper extends Observable {
    public static final String FIREBASE_LINK = "https://vitalize.firebaseio.com/";
    private static final String TAG = DBFireBaseHelper.class.getName();
    private final Firebase firebaseRef;

    public DBFireBaseHelper(Context theContext) {
        Firebase.setAndroidContext(theContext);
        firebaseRef = new Firebase(FIREBASE_LINK);
        if (theContext instanceof MapsActivity) {
            addObserver((MapsActivity) theContext);
        }
    }

    public void insertScrimAreaInFirebase(ScrimArea newArea, String idOfCreator) {
        // Add to firebase
        // Push the new scrim area up to Firebase
        final Firebase vAreaRef = firebaseRef.child("VitalizeAreas").push();
        newArea.setId(vAreaRef.getKey());
        vAreaRef.setValue(newArea, new OnCompleteListener());
        // Add the eventId to the user
        final Firebase userAreaRef = firebaseRef.child("Users").child(firebaseRef.getAuth().getUid()).child("MyAreas");

        addCreatorToGroup(idOfCreator, newArea.getId());
        userAreaRef.push().setValue(vAreaRef.getKey(), new OnCompleteListener());
    }

    private void addCreatorToGroup(String idOfCreator, String idOfEvent ) {
        Firebase eventRef = firebaseRef.child("VitalizeAreas").child(idOfEvent);
        eventRef.child("members").push().setValue(idOfCreator);
    }

    public void updateScrimAreaInFireBase(final ScrimArea area) {
        final Firebase vAreaRef = firebaseRef.child("VitalizeAreas").child(area.getId());
        vAreaRef.setValue(area, new OnCompleteListener());
    }

    public void removeScrimAreaInFirebase(final String vAreaFbId) {
        final Firebase vSingleAreaRef = firebaseRef.child("VitalizeAreas").child(vAreaFbId);
        vSingleAreaRef.removeValue(new OnCompleteListener());
        firebaseRef.child("Users")
                .child(firebaseRef.getAuth().getUid())
                .child("MyAreas").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    final String theId = (String) child.getValue();
                    if (theId.equals(vAreaFbId)) {
                        child.getRef().removeValue(new OnCompleteListener());
                        break;
                    }
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    public List<ScrimArea> getAllScrimAreasFromFirebase() {
        // Get the ScimAreas via Firebase
        final List<ScrimArea> allAreas = new ArrayList<>();

        final Firebase vAreaRef = firebaseRef.child("VitalizeAreas");
        vAreaRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<ScrimArea> updatedVitalizeAreaList = new ArrayList<>();
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    Log.d(TAG, "Child: " + child.toString());
                    final ScrimArea area = child.getValue(ScrimArea.class);
                    for (DataSnapshot mems : child.child("pendingMembers").getChildren()) {
                        final String memId = (String) mems.getValue();
                        getUserAndAddToList(memId, area.getPendingUsers());
                    }
                    area.getPendingUsers();
                    for (DataSnapshot mems : child.child("members").getChildren()) {
                        String memId = (String) mems.getValue();
                        getUserAndAddToList(memId, area.getUsers());
                    }
                    area.setType(area.getType());
                    updatedVitalizeAreaList.add(area);
                }
                VitalizeApplication.setAllAreas(updatedVitalizeAreaList);
                setChanged();
                notifyObservers();
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });

        return allAreas;
    }

    private void getUserAndAddToList(final String userId, final List<User> theList) {
        final Firebase userRef = firebaseRef.child("Users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                /* Once user data is properly filled in firebase we can do this.
                User member = dataSnapshot.getValue(User.class);
                member.setId(dataSnapshot.getKey());
                */

                dataSnapshot = dataSnapshot.child("username");
                String username = "";
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    username = child.getValue(String.class);
                }
                // Temporary data while we fill the rest of user data out in firebase
                User member = new User("jjones:|", username, 128, VitalizeApplication.getAvatarImage(username), R.drawable.moonlightbae);
                member.setId(userId);
                // End temp data
                theList.add(member);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    public void requestToJoinEvent(String eventId) {
        Firebase eventRef = firebaseRef.child("VitalizeAreas").child(eventId).child("pendingMembers");
        eventRef.push().setValue(eventRef.getAuth().getUid());
    }

    public void storeUsername(String userId,final  String username) {
        final Firebase usernameRef = firebaseRef.child("Users").child(userId).child("username");
        usernameRef.push().setValue(username);
    }

    public void retrieveUsernameAndInitialieLocalUser(final String userId) {
        final Firebase usernameRef = firebaseRef.child("Users").child(userId).child("username");
        usernameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String username = "";
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    username = child.getValue(String.class);
                }
                VitalizeApplication.currentUser = new User(username, "", 0, VitalizeApplication.getAvatarImage(username),
                        VitalizeApplication.getAvatarImage(username));
                VitalizeApplication.currentUser.setId(userId);            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });

    }
    public void approveRequestToJoinEvent(String eventId, String userId) {
        Firebase eventRef = firebaseRef.child("VitalizeAreas").child(eventId);
        // Decline just deletes the request from the pending members list.
        declineRequestToJoinEvent(eventId, userId);
        // Then we add the user to the members list
        eventRef.child("members").push().setValue(userId);
    }

    public void declineRequestToJoinEvent(String eventId, final String userId) {
        firebaseRef.child("VitalizeAreas")
                .child(eventId)
                .child("pendingMembers").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    String theId = child.getValue(String.class);
                    if (theId.equals(userId)) {
                        child.getRef().removeValue();
                        break;
                    }
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    public String getUserId() {
        return firebaseRef.getAuth().getUid();
    }

    private class OnCompleteListener implements Firebase.CompletionListener {
        @Override
        public void onComplete(FirebaseError firebaseError, Firebase firebase) {
            if (firebaseError == null) {
                Log.d(TAG, "Success!");
            } else {
                Log.d(TAG, "Error!");
            }
        }
    }

    public void leaveEvent(String eventId, final String userId) {
        firebaseRef.child("VitalizeAreas")
                .child(eventId)
                .child("members").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot child : dataSnapshot.getChildren()) {
                    String theId = child.getValue(String.class);
                    if (theId.equals(userId)) {
                        child.getRef().removeValue();
                        break;
                    }
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

//    public User getUserInfo(String id) {
//        firebaseRef
//    }
}

