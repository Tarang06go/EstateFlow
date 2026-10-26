package com.estateflow.config;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.Path;import java.nio.file.Files;import static org.junit.jupiter.api.Assertions.*;
class UserPreferencesTest{
 @TempDir Path temp;
 @Test void storesReminderPreferencesLocally(){Path file=temp.resolve("preferences.properties");UserPreferences p=new UserPreferences(file);p.updateReminderPreferences(false,true,false);assertDoesNotThrow(p::save);UserPreferences loaded=UserPreferences.load(file);assertFalse(loaded.rentReminders());assertTrue(loaded.leaseReminders());assertFalse(loaded.workOrderReminders());}
 @Test void defaultsAndInvalidValuesRemainSafe(){assertTrue(UserPreferences.defaults().rentReminders());assertTrue(UserPreferences.defaults().leaseReminders());assertTrue(UserPreferences.defaults().workOrderReminders());}
 @Test void malformedReminderValueFallsBackToEnabledDefault() throws Exception{Path file=temp.resolve("preferences.properties");Files.writeString(file,"reminders.rent=maybe\nreminders.leases=false\n");UserPreferences loaded=UserPreferences.load(file);assertTrue(loaded.rentReminders());assertFalse(loaded.leaseReminders());assertTrue(loaded.workOrderReminders());}
}
