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

        buildToolsVersion = "35.0.0";
        jdk = pkgs.jdk17;

        android = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "11.0";
          platformToolsVersion = "35.0.2";
          buildToolsVersions = [ buildToolsVersion ];
          platformVersions = [ "35" ];
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
            jdk
            pkgs.gradle
            pkgs.kotlin
            pkgs.ktlint
          ];

          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
          JAVA_HOME = "${jdk}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${ANDROID_SDK_ROOT}/build-tools/${buildToolsVersion}/aapt2";

          # Gradle needs a writable home
          shellHook = ''
            export GRADLE_USER_HOME="''${GRADLE_USER_HOME:-$HOME/.gradle}"
          '';
        };
      });
}
