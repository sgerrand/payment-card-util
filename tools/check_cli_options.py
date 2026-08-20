"""Compare the command line options this port offers against cardutil's.

The generated config and the vectors are checked by regenerating them. The
command line is hand written, so nothing catches an option cardutil grows, or
one of ours that quietly stops matching. This does.

Run it from the repository root with the CLI jar built:

    mvn package
    python tools/check_cli_options.py

It reads cardutil's own argument parsers rather than its documentation, and
reads ours out of `--help`, so both sides are what the tools actually accept.
Only long option names are compared: argparse and picocli disagree about short
forms in ways nobody types.
"""
import glob
import importlib
import re
import subprocess
import sys

# Our command name, and the cardutil module it is a port of.
COMMANDS = {
    'mci-ipm-to-csv': 'cardutil.cli.mci_ipm_to_csv',
    'mci-csv-to-ipm': 'cardutil.cli.mci_csv_to_ipm',
    'mci-ipm-encode': 'cardutil.cli.mci_ipm_encode',
    'mci-ipm-param-to-csv': 'cardutil.cli.mci_ipm_param_to_csv',
    'mci-ipm-param-encode': 'cardutil.cli.mci_ipm_param_encode',
}

# Options we have and cardutil does not. Each one is a deliberate divergence
# listed in the README; anything else turning up here is a mistake.
EXPECTED_EXTRA = {
    # Card numbers are masked unless this is passed.
    'mci-ipm-to-csv': {'--unmask-pan'},
    # Reads the record length limit from a file, which cardutil can only take
    # from the CARDUTIL_CONFIG environment variable.
    'mci-ipm-param-encode': {'--config-file'},
}

# Options every picocli command has and argparse does not spell the same way.
IGNORED = {'--help', '--version'}


def cardutil_options(module_name):
    """The long option names one cardutil tool accepts."""
    parser = importlib.import_module(module_name).cli_parser()
    names = set()
    for action in parser._actions:  # noqa: SLF001 - argparse has no public list
        names.update(name for name in action.option_strings if name.startswith('--'))
    return names - IGNORED


def our_options(jar, command):
    """The long option names one of our commands accepts, out of its help."""
    help_text = subprocess.run(
        ['java', '-jar', jar, command, '--help'],
        capture_output=True, text=True, check=True).stdout
    # picocli lists each option on its own line, indented. Anything mentioned
    # in a description sits further into the line, so anchoring keeps those out.
    names = set(re.findall(r'^\s{2,6}(?:-\w, )?(--[a-z0-9-]+)', help_text, re.M))
    return names - IGNORED


def main():
    jars = glob.glob('cli/target/payment-card-util-cli-*-all.jar')
    if not jars:
        sys.exit('No CLI jar. Run mvn package first.')
    jar = jars[0]

    problems = []
    for command, module_name in sorted(COMMANDS.items()):
        theirs = cardutil_options(module_name)
        ours = our_options(jar, command)
        allowed = EXPECTED_EXTRA.get(command, set())

        missing = theirs - ours
        extra = ours - theirs - allowed
        unused_allowance = allowed - ours

        for name in sorted(missing):
            problems.append(
                f'{command}: cardutil takes {name} and we do not. A script '
                f'written for cardutil will be turned away.')
        for name in sorted(extra):
            problems.append(
                f'{command}: we take {name} and cardutil does not. Either drop '
                f'it, or list it in the README as a divergence and add it to '
                f'EXPECTED_EXTRA here.')
        for name in sorted(unused_allowance):
            problems.append(
                f'{command}: {name} is listed here as ours alone but the '
                f'command no longer has it. Remove it from EXPECTED_EXTRA.')

        print(f'{command}: {len(ours)} options, {len(theirs)} in cardutil')

    if problems:
        print()
        for problem in problems:
            print(f'::error::{problem}')
        sys.exit(1)

    print('\nEvery tool takes what cardutil takes, plus what the README says.')


if __name__ == '__main__':
    main()
