package org.wikipedia.createaccount;

import android.support.annotation.NonNull;

/**
 * Exception thrown when an account creation request FAILs
 */
class CreateAccountException extends RuntimeException {
    CreateAccountException(@NonNull String message) {
        super(message);
    }
}
