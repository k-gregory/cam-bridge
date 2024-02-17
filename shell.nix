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
}).env
