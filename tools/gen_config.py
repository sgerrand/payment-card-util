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
      f'{len(config["mci_parameter_tables"])} parameter tables')
