{ pkgs ? import <nixpkgs> {} }:

(pkgs.buildFHSUserEnv {
  name = "devbox";
  targetPkgs = pkgs: (with pkgs; [
    sbt
    jdk19
    libnice
    gst_all_1.gstreamer  gst_all_1.gst-plugins-ugly  gst_all_1.gst-plugins-good  gst_all_1.gst-plugins-bad gst_all_1.gst-plugins-base glib
    stdenv.cc.cc.lib
  ]);
  profile = ''
    export A="${pkgs.libnice.out}"
    export GST_PLUGIN_SYSTEM_PATH_1_0="${pkgs.gst_all_1.gstreamer.out}/lib/gstreamer-1.0/:${pkgs.gst_all_1.gst-plugins-base}/lib/gstreamer-1.0/:${pkgs.gst_all_1.gst-plugins-good}/lib/gstreamer-1.0/:${pkgs.gst_all_1.gst-plugins-bad}/lib/gstreamer-1.0/:${pkgs.gst_all_1.gst-plugins-ugly}/lib/gstreamer-1.0/:${pkgs.libnice.out}/lib/gstreamer-1.0"
  '';
}).env
