{
  description = "uploid - upload your images";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        };

        android = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "11.0";
          platformToolsVersion = "35.0.2";
          buildToolsVersions = [ "35.0.0" ];
          platformVersions = [ "35" "30" ];
          includeNDK = false;
          includeEmulator = false;
          includeSystemImages = false;
        };

        androidSdk = android.androidsdk;
      in
      {
        devShells.default = pkgs.mkShell rec {
          buildInputs = [
            androidSdk
            pkgs.jdk17
            pkgs.gradle
            pkgs.kotlin
            pkgs.ktlint
          ];

          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
          JAVA_HOME = "${pkgs.jdk17}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${ANDROID_SDK_ROOT}/build-tools/34.0.0/aapt2";

          # Gradle needs a writable home
          shellHook = ''
            export GRADLE_USER_HOME="''${GRADLE_USER_HOME:-$HOME/.gradle}"
          '';
        };
      });
}
