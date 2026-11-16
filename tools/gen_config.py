"""Generate DefaultConfig.java from the Python cardutil config.

Run with the venv python from the `gen` directory so that the installed
cardutil package is picked up rather than the checked out sources.
"""
import json
import sys
import cardutil
from cardutil.config import config

VERSION = getattr(cardutil, '__version__', 'unknown')

OUT = sys.argv[1]

FIELD_TYPE = {'FIXED': 'FieldType.FIXED', 'LLVAR': 'FieldType.LLVAR', 'LLLVAR': 'FieldType.LLLVAR'}
VALUE_TYPE = {None: 'ValueType.TEXT', 'int': 'ValueType.LONG', 'long': 'ValueType.LONG',
              'decimal': 'ValueType.DECIMAL', 'datetime': 'ValueType.DATETIME'}
PROCESSOR = {None: 'null', 'PDS': 'FieldProcessors.PDS', 'DE43': 'FieldProcessors.DE43',
             'ICC': 'FieldProcessors.ICC', 'PAN': 'FieldProcessors.PAN',
             'PAN-PREFIX': 'FieldProcessors.PAN_PREFIX'}


# Every key this generator knows what to do with, at each level of the config.
# Anything else stops the run. A key nobody handles is a cardutil feature this
# port does not have yet, and dropping it quietly is how a release that changes
# behaviour comes to look like a version bump.
KNOWN_KEYS = {
    'config': {'MAX_VBS_RECORD_LENGTH', 'bit_config', 'output_data_elements',
               'mci_parameter_tables', 'PDS'},
    'bit_config field': {'field_name', 'field_type', 'field_length', 'field_python_type',
                         'field_date_format', 'field_processor', 'field_processor_config'},
    'PDS entry': {'subfields'},
    'PDS subfield': {'field_start', 'field_length', 'field_name', 'field_python_type'},
    'parameter table field': {'start', 'end'},
}

# cardutil converts a subfield for these two and leaves anything else as text,
# 'long' and 'datetime' included. See PdsSubfield.valueTypeFor.
SUBFIELD_VALUE_TYPE = {'int': 'ValueType.LONG', 'decimal': 'ValueType.DECIMAL'}


def check_keys(kind, mapping, where):
    unknown = sorted(set(mapping) - KNOWN_KEYS[kind])
    if unknown:
        sys.exit(f'{where} has keys this generator does not handle: {", ".join(unknown)}. '
                 f'cardutil {VERSION} has added to its config, so the port needs to learn '
                 f'what they do before regenerating.')


check_keys('config', config, 'the cardutil config')
for bit, field in config['bit_config'].items():
    check_keys('bit_config field', field, f'bit_config {bit}')
for pds_tag, entry in config.get('PDS', {}).items():
    check_keys('PDS entry', entry, f'PDS {pds_tag}')
    for number, part in entry.get('subfields', {}).items():
        check_keys('PDS subfield', part, f'PDS {pds_tag} subfield {number}')
for table_id, fields in config['mci_parameter_tables'].items():
    for name, position in fields.items():
        check_keys('parameter table field', position, f'parameter table {table_id} {name}')


def jstr(value):
    """Python string -> Java string literal. JSON escaping is a subset of Java's."""
    if value is None:
        return 'null'
    return json.dumps(value)


lines = []
w = lines.append

w('package com.sgerrand.paymentcardutil.config;')
w('')
w('import java.util.LinkedHashMap;')
w('import java.util.List;')
w('import java.util.Map;')
w('')
w('/**')
w(' * The Mastercard IPM message and parameter file layout.')
w(' *')
w(' * <p>Generated from the config of the Python cardutil package, version')
w(f' * {VERSION}. Do not edit by hand; see tools/gen_config.py.')
w(' *')
w(' * @see <a href="https://github.com/adelosa/cardutil">adelosa/cardutil</a>')
w(' */')
w('final class DefaultConfig {')
w('')
w('    private DefaultConfig() {')
w('    }')
w('')
w('    static IsoConfig build() {')
w('        return IsoConfig.builder()')
w(f'                .maxVbsRecordLength({config.get("MAX_VBS_RECORD_LENGTH", 6000)})')
w('                .bitConfig(bitConfig())')
w('                .outputDataElements(outputDataElements())')
w('                .parameterTables(parameterTables())')
w('                .pdsSubfields(pdsSubfields())')
w('                .build();')
w('    }')
w('')
w('    private static Map<Integer, FieldConfig> bitConfig() {')
w('        Map<Integer, FieldConfig> fields = new LinkedHashMap<>();')

for bit in sorted(config['bit_config'], key=int):
    c = config['bit_config'][bit]
    args = ', '.join([
        jstr(c['field_name']),
        FIELD_TYPE[c['field_type']],
        str(c['field_length']),
        VALUE_TYPE[c.get('field_python_type')],
        jstr(c.get('field_date_format')),
        PROCESSOR[c.get('field_processor')],
        jstr(c.get('field_processor_config')),
    ])
    w(f'        fields.put({bit}, new FieldConfig(')
    w(f'                {args}));')

w('        return fields;')
w('    }')
w('')
w('    private static List<String> outputDataElements() {')
w('        return List.of(')
keys = config['output_data_elements']
for i in range(0, len(keys), 5):
    chunk = ', '.join(jstr(k) for k in keys[i:i + 5])
    end = ');' if i + 5 >= len(keys) else ','
    w(f'                {chunk}{end}')
w('    }')
w('')
w('    private static Map<String, ParamTable> parameterTables() {')
w('        Map<String, ParamTable> tables = new LinkedHashMap<>();')
for table_id in config['mci_parameter_tables']:
    w(f'        tables.put({jstr(table_id)}, table{table_id}());')
w('        return tables;')
w('    }')

w('')
w('    private static Map<String, Map<String, PdsSubfield>> pdsSubfields() {')
w('        Map<String, Map<String, PdsSubfield>> subfields = new LinkedHashMap<>();')
for pds_tag, entry in config.get('PDS', {}).items():
    if not entry.get('subfields'):
        continue
    w(f'        Map<String, PdsSubfield> pds{pds_tag} = new LinkedHashMap<>();')
    for number, part in entry['subfields'].items():
        args = ', '.join([
            jstr(part.get('field_name', '')),
            str(part.get('field_start', 0)),
            str(part.get('field_length', 0)),
            SUBFIELD_VALUE_TYPE.get(part.get('field_python_type', 'string'), 'ValueType.TEXT'),
        ])
        w(f'        pds{pds_tag}.put({jstr(number)}, new PdsSubfield({args}));')
    w(f'        subfields.put({jstr(pds_tag)}, pds{pds_tag});')
w('        return subfields;')
w('    }')

for table_id, fields in config['mci_parameter_tables'].items():
    w('')
    w(f'    private static ParamTable table{table_id}() {{')
    w('        Map<String, ParamTable.Position> fields = new LinkedHashMap<>();')
    for name, pos in fields.items():
        w(f'        fields.put({jstr(name)}, new ParamTable.Position({pos["start"]}, {pos["end"]}));')
    w(f'        return new ParamTable({jstr(table_id)}, fields);')
    w('    }')

w('}')

with open(OUT, 'w') as f:
    f.write('\n'.join(lines) + '\n')

print(f'wrote {OUT}: {len(lines)} lines, {len(config["bit_config"])} data elements, '
      f'{len(config["output_data_elements"])} csv columns, '
      f'{len(config.get("PDS", {}))} split private data subelements, '
      f'{len(config["mci_parameter_tables"])} parameter tables')
