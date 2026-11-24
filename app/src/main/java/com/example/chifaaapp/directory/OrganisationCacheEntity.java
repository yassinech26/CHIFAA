package com.example.chifaaapp.directory;
import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName="directory_cache")
public class OrganisationCacheEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String json = "";
    public long synchronizedAt;
}
