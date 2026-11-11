package com.example.gamestore.dto;

public class PreferencesUpdateDto {
    private Boolean publicProfile;
    private Boolean emailNotifications;
    private Boolean gameUpdates;

    public Boolean getPublicProfile() { return publicProfile; }
    public void setPublicProfile(Boolean publicProfile) { this.publicProfile = publicProfile; }
    public Boolean getEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(Boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    public Boolean getGameUpdates() { return gameUpdates; }
    public void setGameUpdates(Boolean gameUpdates) { this.gameUpdates = gameUpdates; }
}
