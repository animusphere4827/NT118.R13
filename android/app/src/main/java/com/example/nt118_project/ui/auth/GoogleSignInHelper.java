package com.example.nt118_project.ui.auth;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.example.nt118_project.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/**
 * Lấy Google ID token bằng Credential Manager (không dùng Firebase).
 * ID token sau đó được gửi lên POST /auth/google để server cấp JWT.
 */
public final class GoogleSignInHelper {

    private static final String TAG = "GoogleSignIn";

    public interface Callback {
        void onIdToken(String idToken);

        /** Người dùng đóng hộp chọn tài khoản: không báo lỗi. */
        void onCancelled();

        void onError(String message);
    }

    private GoogleSignInHelper() {
    }

    public static void signIn(Activity activity, String webClientId, Callback callback) {
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            try {
                                callback.onIdToken(GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken());
                            } catch (Exception e) {
                                Log.w(TAG, "Không đọc được Google ID token", e);
                                callback.onError(activity.getString(R.string.error_google_failed));
                            }
                        } else {
                            callback.onError(activity.getString(R.string.error_google_failed));
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            callback.onCancelled();
                        } else if (e instanceof NoCredentialException) {
                            callback.onError(activity.getString(R.string.error_google_no_account));
                        } else {
                            Log.w(TAG, "Credential Manager lỗi: " + e.getType(), e);
                            callback.onError(activity.getString(R.string.error_google_failed));
                        }
                    }
                });
    }

    /** Gọi khi đăng xuất để lần sau hộp chọn tài khoản hiện lại thay vì tự chọn. */
    public static void clearState(Context context) {
        CredentialManager.create(context).clearCredentialStateAsync(new ClearCredentialStateRequest(), null,
                ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void unused) {
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        Log.w(TAG, "Không xóa được trạng thái Credential Manager", e);
                    }
                });
    }
}
