#!/usr/bin/env bash
#
# Checks that a shaded command line jar actually runs.
#
# "mvn verify" exercises the classes on the classpath, which says nothing
# about the jar people download. Shading is its own step with its own ways to
# go wrong: a manifest with no main class, a service file one dependency
# silently overwrote, a missing entry that only a reflective lookup wanted.
# None of that shows up until someone runs the thing.
#
# So this drives the jar the way a person would, and asserts on what comes
# back. It deliberately does not retest the conversion logic, which the unit
# tests already cover in depth; it asks whether the packaged tool works at
# all.
#
# Usage: tools/smoke_cli.sh <jar> [expected version]
#
# Given an expected version it insists the jar reports exactly that, which is
# how the release checks the jar it is about to attach really is the version
# on the tag. Without one it only insists the jar knows its own version.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <jar> [expected version]" >&2
    exit 2
fi

jar=$1
expected_version=${2:-}

if [ ! -f "$jar" ]; then
    echo "no such jar: $jar" >&2
    exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# A published Mastercard test number, as used by the unit tests. Never a real
# card number, here or anywhere else in this repository.
PAN=5555444433332222

run() {
    java -jar "$jar" "$@"
}

pass=0

ok() {
    printf '  ok    %s\n' "$1"
    pass=$((pass + 1))
}

fail() {
    printf '  FAIL  %s\n' "$1" >&2
    exit 1
}

# Nothing below works if the manifest has no main class, so this is also the
# check that shading produced a runnable jar at all.
echo "Help and version"

help=$(run --help)
for command in mci-ipm-to-csv mci-csv-to-ipm mci-ipm-encode mci-ipm-param-to-csv \
    mci-ipm-param-encode; do
    case "$help" in
        *"$command"*) ;;
        *) fail "the top level help does not mention $command" ;;
    esac
done
ok "the help lists all five tools"

version=$(run --version)
case "$version" in
    *"(from source)"*)
        fail "the jar does not know its own version: $version"
        ;;
esac
if [ -n "$expected_version" ] && [ "$version" != "payment-card-util $expected_version" ]; then
    fail "expected version $expected_version, the jar says: $version"
fi
ok "the version reads $version"

# picocli finds subcommands by reflection over annotations, so a shading
# mistake tends to show up as a subcommand that cannot be reached.
for command in mci-ipm-to-csv mci-csv-to-ipm mci-ipm-encode mci-ipm-param-to-csv \
    mci-ipm-param-encode; do
    run "$command" --help > /dev/null || fail "$command --help did not run"
done
ok "every tool answers its own help"

echo "Reading and writing files"

printf 'MTI,DE2,DE4,DE12,DE37\n1240,%s,12345,2020-03-04 05:06:07,REF00000001\n' "$PAN" \
    > "$work/in.csv"

run mci-csv-to-ipm "$work/in.csv" -o "$work/out.ipm" > /dev/null \
    || fail "mci-csv-to-ipm did not run"
[ -s "$work/out.ipm" ] || fail "mci-csv-to-ipm wrote nothing"
ok "a csv becomes an ipm file"

run mci-ipm-to-csv "$work/out.ipm" -o "$work/back.csv" --unmask-pan > /dev/null \
    || fail "mci-ipm-to-csv did not run"

# The values have been through the ISO 8583 layer in both directions, so
# finding them again means rather more than the command exiting zero.
for value in 1240 "$PAN" 12345 '2020-03-04 05:06:07'; do
    grep --quiet -- "$value" "$work/back.csv" \
        || fail "$value did not survive the round trip"
done
ok "the values come back out again"

# Masking unless asked otherwise is one of this port's deliberate divergences
# from cardutil, and it is the sort of thing worth noticing if it ever stops
# happening in a released jar.
run mci-ipm-to-csv "$work/out.ipm" -o "$work/masked.csv" > /dev/null
if grep --quiet -- "$PAN" "$work/masked.csv"; then
    fail "the card number was not masked without --unmask-pan"
fi
grep --quiet -- '555544\*\*\*\*\*\*2222' "$work/masked.csv" \
    || fail "the card number is neither masked nor present, which is odd"
ok "the card number is masked unless asked for"

echo "Character sets"

# Clearing files are usually EBCDIC. Going out to cp500 and back exercises the
# encoding path, which is where a stripped charset provider would show up.
run mci-ipm-encode "$work/out.ipm" -o "$work/ebcdic.ipm" \
    --in-encoding latin_1 --out-encoding cp500 > /dev/null \
    || fail "mci-ipm-encode did not run"
run mci-ipm-to-csv "$work/ebcdic.ipm" -o "$work/ebcdic.csv" \
    --in-encoding cp500 --unmask-pan > /dev/null \
    || fail "the re-encoded file could not be read back"
grep --quiet -- "$PAN" "$work/ebcdic.csv" \
    || fail "the card number did not survive the trip through cp500"
ok "a file re-encoded to cp500 reads back"

echo "Configuration files"

# The only thing that makes the jar need Jackson, so this is the check that
# the shaded copy of it works.
cat > "$work/layout.json" <<'JSON'
{
  "bit_config": {
    "2": {"field_name": "Card number", "field_type": "LLVAR", "field_length": 19},
    "4": {"field_name": "Amount transaction", "field_type": "FIXED",
          "field_length": 12, "field_python_type": "long"},
    "12": {"field_name": "Date/Time local transaction", "field_type": "FIXED",
           "field_length": 12, "field_python_type": "datetime",
           "field_date_format": "%y%m%d%H%M%S"},
    "37": {"field_name": "Retrieval reference number", "field_type": "FIXED",
           "field_length": 12}
  },
  "output_data_elements": ["MTI", "DE2", "DE4"]
}
JSON

run mci-ipm-to-csv "$work/out.ipm" -o "$work/configured.csv" \
    --config-file "$work/layout.json" --unmask-pan > /dev/null \
    || fail "mci-ipm-to-csv did not run with a config file"

header=$(head -1 "$work/configured.csv" | tr -d '\r')
[ "$header" = "MTI,DE2,DE4" ] \
    || fail "expected the configured columns MTI,DE2,DE4, got: $header"
ok "a json layout changes the columns"

echo
echo "$pass checks passed against $(basename "$jar")."
