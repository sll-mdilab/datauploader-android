package net.sll_mdilab.datauploader.exception;


public class UnauthorizedException extends RuntimeException {

    private String mAuthToken;

    public UnauthorizedException( Throwable throwable, String authToken) {
        super(throwable);

        mAuthToken = authToken;
    }
    public String getAuthToken() {
        return mAuthToken;
    }

    public void setAuthToken(String authToken) {
        mAuthToken = authToken;
    }

}
