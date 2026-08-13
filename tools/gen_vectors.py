"""Generate ISO 8583 and IPM golden vectors from the Python cardutil package.

The Java tests replay these to prove the port produces the same bytes and the
same values as cardutil does.

Usage, from a directory that is not the checked out cardutil source::

    python gen_vectors.py <output.json>
"""
import binascii
import datetime
import decimal
import io
import json
import sys

import cardutil
from cardutil import card, iso8583, key, mciipm, pinblock

OUT = sys.argv[1]


def tag(value):
    """Tag a Python value so the Java side can rebuild it with its type."""
    if isinstance(value, bool):
        raise TypeError('booleans are not a message value type')
    if isinstance(value, bytes):
        return {'t': 'bytes', 'v': binascii.hexlify(value).decode()}
    if isinstance(value, datetime.datetime):
        return {'t': 'datetime', 'v': value.isoformat()}
    if isinstance(value, decimal.Decimal):
        return {'t': 'decimal', 'v': str(value)}
    if isinstance(value, int):
        return {'t': 'int', 'v': str(value)}
    if isinstance(value, str):
        return {'t': 'str', 'v': value}
    raise TypeError(f'unhandled value type {type(value)}: {value!r}')


def tagged(mapping):
    return {key: tag(value) for key, value in mapping.items()}


def iso_case(name, message, encoding=None, hex_bitmap=False):
    """Round trip a message dict through cardutil and record what came out."""
    raw = iso8583.dumps(dict(message), encoding=encoding, hex_bitmap=hex_bitmap)
    parsed = iso8583.loads(raw, encoding=encoding, hex_bitmap=hex_bitmap)
    return {
        'name': name,
        'encoding': encoding or 'latin_1',
        'hex_bitmap': hex_bitmap,
        'input': tagged(message),
        'message_hex': binascii.hexlify(raw).decode(),
        'parsed': tagged(parsed),
    }


def ipm_case(name, messages, encoding=None, blocked=False):
    """Write messages to an IPM file with cardutil, then read them back."""
    out = io.BytesIO()
    writer = mciipm.IpmWriter(out, encoding=encoding, blocked=blocked)
    for message in messages:
        writer.write(dict(message))
    writer.close()
    file_bytes = out.getvalue()

    reader = mciipm.IpmReader(io.BytesIO(file_bytes), encoding=encoding, blocked=blocked)
    read_back = [tagged(record) for record in reader]

    return {
        'name': name,
        'encoding': encoding or 'latin_1',
        'blocked': blocked,
        'input': [tagged(message) for message in messages],
        'file_hex': binascii.hexlify(file_bytes).decode(),
        'parsed': read_back,
    }


def vbs_case(name, records, blocked=False):
    file_bytes = mciipm.vbs_list_to_bytes(records, blocked=blocked)
    return {
        'name': name,
        'blocked': blocked,
        'input': [binascii.hexlify(record).decode() for record in records],
        'file_hex': binascii.hexlify(file_bytes).decode(),
    }


# A DE43 value laid out the way the default config's pattern expects:
# name \ address \ suburb \ 10 char postcode, 3 char state, 3 char country.
DE43 = 'BIG SHOP           \\123 FAKE ST        \\SYDNEY             \\2000      NSWAUS'

# DE55 chip data: 9F02 (amount), 5F2A (currency), 82 (AIP), then low values.
ICC_HEX = '9f0206000000001000' '5f2a020036' '820200' '00'

ISO_CASES = [
    iso_case('simple', {'MTI': '1144', 'DE2': '4444555566667777'}),
    iso_case('simple cp500', {'MTI': '1144', 'DE2': '4444555566667777'}, encoding='cp500'),
    iso_case('simple hex bitmap', {'MTI': '1144', 'DE2': '4444555566667777'}, hex_bitmap=True),
    iso_case('simple cp037', {'MTI': '1144', 'DE2': '4444555566667777'}, encoding='cp037'),
    iso_case('numeric fields', {
        'MTI': '1240', 'DE2': '4444555566667777', 'DE3': '000000', 'DE4': 12345,
        'DE5': 12345, 'DE6': 12345, 'DE22': '123456789012',
    }),
    iso_case('zero amount is kept', {'MTI': '1240', 'DE2': '4444555566667777', 'DE4': 0}),
    iso_case('datetime field', {
        'MTI': '1240', 'DE2': '4444555566667777',
        'DE12': datetime.datetime(2020, 3, 4, 5, 6, 7),
    }),
    iso_case('datetime last century', {
        'MTI': '1240', 'DE2': '4444555566667777',
        'DE12': datetime.datetime(1995, 12, 31, 23, 59, 59),
    }),
    iso_case('fixed text is space padded', {
        'MTI': '1240', 'DE2': '4444555566667777', 'DE37': 'REF123', 'DE38': 'AUTH1',
    }),
    iso_case('llvar and lllvar', {
        'MTI': '1240', 'DE2': '4444555566667777', 'DE43': DE43, 'DE54': 'ADDITIONAL AMOUNTS',
    }),
    iso_case('de43 breakdown', {'MTI': '1240', 'DE43': DE43}),
    iso_case('pds fields', {
        'MTI': '1240', 'DE2': '4444555566667777',
        'PDS0023': 'PDS23VALUE', 'PDS0052': 'A', 'PDS0158': '0000000000',
    }),
    iso_case('pds fields cp500', {
        'MTI': '1240', 'PDS0023': 'PDS23VALUE', 'PDS0158': '0000000000',
    }, encoding='cp500'),
    iso_case('icc data', {
        'MTI': '1240', 'DE2': '4444555566667777', 'DE55': binascii.unhexlify(ICC_HEX),
    }),
    iso_case('secondary bitmap fields', {
        'MTI': '1240', 'DE2': '4444555566667777', 'DE71': 1, 'DE73': '250101',
        'DE93': '123456789', 'DE94': '123456789', 'DE95': '1234567890123456789012',
        'DE100': '123456789',
    }),
    iso_case('every configured field type', {
        'MTI': '1240', 'DE2': '4444555566667777', 'DE3': '000000', 'DE4': 100,
        'DE12': datetime.datetime(2021, 6, 1, 12, 0, 0), 'DE24': '200',
        'DE25': '00', 'DE26': '01', 'DE30': '000000000000', 'DE31': 'X',
        'DE33': '123456789', 'DE37': 'ACQREF123456', 'DE38': 'AUTH01',
        'DE40': '000', 'DE41': 'TERMID12', 'DE42': 'CARDACCEPTORID ',
        'DE49': '036', 'DE50': '036', 'DE63': '1234567890',
    }),
]

IPM_CASES = [
    ipm_case('single record', [{'MTI': '1144', 'DE2': '4444555566667777'}]),
    ipm_case('three records', [
        {'MTI': '1144', 'DE2': '4444555566667777'},
        {'MTI': '1240', 'DE2': '5555444433332222', 'DE4': 99},
        {'MTI': '1644', 'DE2': '4444555566667777', 'PDS0158': '0000000000'},
    ]),
    ipm_case('blocked', [
        {'MTI': '1144', 'DE2': '4444555566667777'},
        {'MTI': '1240', 'DE2': '5555444433332222', 'DE4': 99},
    ], blocked=True),
    ipm_case('cp500 blocked', [
        {'MTI': '1240', 'DE2': '4444555566667777', 'DE4': 12345, 'DE43': DE43},
    ], encoding='cp500', blocked=True),
    # Long enough to spill over a 1012 byte block boundary.
    ipm_case('spans several blocks', [
        {'MTI': '1240', 'DE2': '4444555566667777', 'DE4': n, 'DE54': 'X' * 100}
        for n in range(1, 30)
    ], blocked=True),
]

VBS_CASES = [
    vbs_case('two records', [b'This is first record 1234567', b'This is second record AAAABBBBB123']),
    vbs_case('two records blocked',
             [b'This is first record 1234567', b'This is second record AAAABBBBB123'], blocked=True),
    vbs_case('empty file', []),
    vbs_case('empty file blocked', [], blocked=True),
    vbs_case('record exactly one block', [b'A' * 1008], blocked=True),
    vbs_case('record spanning blocks', [b'B' * 2000], blocked=True),
]

CARD_NUMBERS = [
    '1111222233334444', '4111111111111111', '5555555555554444',
    '378282246310005', '6011111111111117', '4444555566667777',
]

CARD_CASES = [
    {
        'card_number': number,
        'check_digit': card.calculate_check_digit(number[:-1]),
        'masked': card.mask(number),
    }
    for number in CARD_NUMBERS
]

PIN_KEY = '00' * 16
AES_KEY = '00' * 16

ISO0_CASES = [
    {
        'pin': pin,
        'card_number': number,
        'block_hex': binascii.hexlify(pinblock.Iso0PinBlock(pin=pin, card_number=number).to_bytes()).decode(),
        'key': PIN_KEY,
        'encrypted_hex': binascii.hexlify(
            pinblock.Iso0TDESPinBlockWithVisaPVV(pin=pin, card_number=number).to_enc_bytes(key=PIN_KEY)).decode(),
        # A PVV is only defined for a 4 digit pin: the block it is worked out
        # from is 11 card digits, a key index and the pin, and that must come to
        # exactly 16 digits.
        'pvv_key': PIN_KEY if len(pin) == 4 else None,
        'pvv_key_index': 1,
        'pvv': pinblock.calculate_pvv(pin, PIN_KEY, 1, number) if len(pin) == 4 else None,
    }
    # Pins of 10 digits or more are left out: cardutil writes the length as
    # decimal text there, which produces a block that cannot be read back.
    for pin, number in [
        ('1234', '1111222233334444'),
        ('9999', '4111111111111111'),
        ('12345', '5555555555554444'),
        ('123456', '4444555566667777'),
        ('123456789', '4444555566667777'),
    ]
]

ISO4_CASES = [
    {
        'pin': pin,
        'random_hex': f'{random_value:016x}',
        'block_hex': binascii.hexlify(
            pinblock.Iso4PinBlock(pin=pin, random_value=random_value).to_bytes()).decode(),
        'key': AES_KEY,
        'encrypted_hex': binascii.hexlify(
            pinblock.Iso4AESPinBlockWithVisaPVV(
                pin=pin, random_value=random_value).to_enc_bytes(key=AES_KEY)).decode(),
    }
    for pin, random_value in [
        ('1234', 0x837c658036105d19),
        ('9999', 0x0123456789abcdef),
        ('123456789', 0xffffffffffffffff),
    ]
]

KEY_CASES = []
for parts in [
    ('6D6BE51F04F76167491554FE25F7ABEF',),
    ('6D6BE51F04F76167491554FE25F7ABEF', '67499B2CF137DFCB9EA28FF757CD10A7'),
    ('00' * 16, 'FF' * 16),
]:
    clear, kcv = key.get_zone_master_key(*parts)
    enc, enc_kcv = key.get_enc_zone_master_key('00' * 16, *parts)
    KEY_CASES.append({
        'components': list(parts),
        'clear_key': clear,
        'kcv': kcv,
        'master_key': '00' * 16,
        'encrypted_key': enc,
        'encrypted_kcv': enc_kcv,
    })


def param_file(table_id, rows, blocked=False, expanded=False):
    """Build an IPM parameter extract holding one table, then read it back."""
    from cardutil.config import config as cardutil_config
    layout = cardutil_config['mci_parameter_tables'][table_id]
    sub_id = '001'

    records = []

    # Index record: says that sub id 001 means our table.
    index = list(' ' * 250)
    index[11:19] = list('IP0000T1')
    index[19:27] = list(table_id)
    index[243:246] = list(sub_id)
    records.append(''.join(index).encode('latin_1'))
    records.append(b'TRAILER RECORD IP0000T1' + b' ' * 30)

    for row in rows:
        # Compressed layout: 7 char timestamp, 1 char active code, 3 char sub id,
        # then the fields, each shifted 8 left of where the config puts them.
        size = max(position['end'] for position in layout.values())
        record = list(' ' * size)
        record[0:7] = list(row['timestamp'])
        record[7:8] = list(row['active'])
        record[8:11] = list(sub_id)
        for name, value in row['fields'].items():
            start = layout[name]['start'] - 8
            end = layout[name]['end'] - 8
            padded = value.ljust(end - start)[:end - start]
            record[start:end] = list(padded)
        records.append(''.join(record).encode('latin_1'))

    file_bytes = mciipm.vbs_list_to_bytes(records, blocked=blocked)

    reader = mciipm.IpmParamReader(
        io.BytesIO(file_bytes), table_id=table_id, blocked=blocked, expanded=expanded)
    return {
        'name': f'{table_id}{" blocked" if blocked else ""}',
        'table_id': table_id,
        'blocked': blocked,
        'expanded': expanded,
        'file_hex': binascii.hexlify(file_bytes).decode(),
        'parsed': [dict(record) for record in reader],
    }


PARAM_ROWS = [
    {'timestamp': '2503101', 'active': 'A', 'fields': {
        'issuer_account_range_low': '5555550000000000000',
        'gcms_product_id': 'MCC',
        'issuer_account_range_high': '5555559999999999999',
        'card_program_identifier': 'MCC',
        'member_id': '00000012345',
        'card_country_alpha': 'AUS',
    }},
    {'timestamp': '2503102', 'active': 'I', 'fields': {
        'issuer_account_range_low': '4444440000000000000',
        'gcms_product_id': 'VSA',
        'issuer_account_range_high': '4444449999999999999',
        'card_program_identifier': 'VSA',
        'member_id': '00000067890',
        'card_country_alpha': 'NZL',
    }},
]

PARAM_CASES = [
    param_file('IP0040T1', PARAM_ROWS),
    param_file('IP0040T1', PARAM_ROWS, blocked=True),
]

data = {
    'cardutil_version': cardutil.__version__,
    'iso8583': ISO_CASES,
    'ipm': IPM_CASES,
    'vbs': VBS_CASES,
    'card': CARD_CASES,
    'iso0_pinblock': ISO0_CASES,
    'iso4_pinblock': ISO4_CASES,
    'keys': KEY_CASES,
    'param': PARAM_CASES,
}

with open(OUT, 'w') as f:
    json.dump(data, f, indent=2, sort_keys=False)
    f.write('\n')

print(f'wrote {OUT}: ' + ', '.join(
    f'{len(cases)} {name}' for name, cases in data.items() if isinstance(cases, list))
    + f', from cardutil {cardutil.__version__}')
