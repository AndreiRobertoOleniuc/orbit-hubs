package com.orbit.onboarding;

/**
 * Published entry point for source linking (e.g., GitHub, Zefix) during user
 * onboarding. Writes into the profile module via its published API.
 */
public interface OnboardingApi {

    default void startOnboarding() {
        throw new UnsupportedOperationException("not implemented");
    }

}
