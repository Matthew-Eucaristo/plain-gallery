# Navigation keys and collections are restored with kotlinx.serialization.
-keepclassmembers @kotlinx.serialization.Serializable class app.plaingallery.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
