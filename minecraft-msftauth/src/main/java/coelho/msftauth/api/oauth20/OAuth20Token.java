package coelho.msftauth.api.oauth20;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonObject;

@SuppressWarnings("FieldMayBeFinal")
public class OAuth20Token {
    @SerializedName("access_token")
    private String accessToken;
    @SerializedName("expires_in")
    private long expiresIn;
    @SerializedName("foci")
    private String foci;
    @SerializedName("refresh_token")
    private String refreshToken;
    @SerializedName("scope")
    private String scope;
    @SerializedName("token_type")
    private String tokenType;
    @SerializedName("user_id")
    private String userId;

    public OAuth20Token(String tokenType, long expiresIn, String scope, String accessToken, String refreshToken, String userId, String foci) {
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.scope = scope;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.foci = foci;
    }

    public String getTokenType() {
        return this.tokenType;
    }

    public long getExpiresIn() {
        return this.expiresIn;
    }

    public String getScope() {
        return this.scope;
    }

    public String getAccessToken() {
        return this.accessToken;
    }

    public String getRefreshToken() {
        return this.refreshToken;
    }

    public String getUserId() {
        return this.userId;
    }

    public String getFoci() {
        return this.foci;
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("access_token", this.accessToken);
        obj.addProperty("expires_in", this.expiresIn);
        if (this.foci != null) {
            obj.addProperty("foci", this.foci);
        }
        obj.addProperty("refresh_token", this.refreshToken);
        obj.addProperty("scope", this.scope);
        obj.addProperty("token_type", this.tokenType);
        obj.addProperty("user_id", this.userId);
        return obj;
    }

    public String toJsonString() {
        return toJsonObject().toString();
    }
}
