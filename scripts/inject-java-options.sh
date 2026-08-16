#!/bin/sh
# After syncing the proto from aleksUIX/vastlint, restore the Java codegen
# options. Those options are not on the canonical proto: adding them there
# would fail `buf breaking` FILE against main.
set -eu
file=${1:?proto file}

if grep -q 'option java_package' "$file"; then
  exit 0
fi

tmp=$file.javaopts
awk '
  /^package / {
    print
    print ""
    print "// Java generated code lives under io.openadtech.vastlint.v1 so the Maven"
    print "// coordinates and the Java package share a namespace. The wire package name"
    print "// is unchanged."
    print "option java_package = \"io.openadtech.vastlint.v1\";"
    print "option java_multiple_files = true;"
    print "option java_outer_classname = \"VastlintProto\";"
    next
  }
  { print }
' "$file" > "$tmp"
mv "$tmp" "$file"
