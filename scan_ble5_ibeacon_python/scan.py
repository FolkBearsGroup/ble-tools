"""
BLE iBeacon scanner in Python (cross-platform, bleak).
BLE5 拡張広告も受信するため scanning_mode=active と PHY all を指定。

install:
    pip install bleak
run:
    python scan.py

	Ubuntu 上で動かすと、BLE5 拡張広告も受信できる
	Windows 上では BLE5 拡張広告は受信できていない

"""

import asyncio
import datetime as _dt
from typing import Dict

from bleak import BleakScanner


APPLE_COMPANY_ID = 0x004C
IBEACON_PREAMBLE = bytes([0x02, 0x15])


def _format_mac(address: str) -> str:
	parts = address.split(":")
	if len(parts) == 6:
		return address.upper()
	return address.upper()


def _now_str() -> str:
	return _dt.datetime.now().strftime("%Y/%m/%d %H:%M:%S.%f")[:23]


def _parse_ibeacon(mfg_data: Dict[int, bytes]):
	data = mfg_data.get(APPLE_COMPANY_ID)
	if not data:
		return None
	if len(data) < 23:
		return None
	if not data.startswith(IBEACON_PREAMBLE):
		return None

	uuid_bytes = data[2:18]
	major = data[18] << 8 | data[19]
	minor = data[20] << 8 | data[21]
	tx_power = int.from_bytes(data[22:23], byteorder="big", signed=True)
	return uuid_bytes, major, minor, tx_power


def _detection_callback(device, advertisement_data) -> None:
	parsed = _parse_ibeacon(advertisement_data.manufacturer_data or {})
	if not parsed:
		return

	uuid_bytes, major, minor, tx_power = parsed
	time = _now_str()
	rssi = advertisement_data.rssi
	mac = _format_mac(device.address or "")
	print(
		f"{time} [{uuid_bytes.hex()}] rssi={rssi} tx={tx_power} major=0x{major:04x} minor=0x{minor:04x} mac={mac}"
	)


async def main() -> None:
	print("Folkbears iBeaconCheck (bleak, BLE5 extended)")
	scanner = BleakScanner(
		detection_callback=_detection_callback,
		scanning_mode="active",  # BLE5 拡張広告を拾いやすくする
		scanning_filter={"scan_phy": "le_all"},  # バックエンドが対応していれば全PHYスキャン
	)
	await scanner.start()
	print("Press Enter to stop")
	try:
		await asyncio.get_event_loop().run_in_executor(None, input)
	finally:
		await scanner.stop()


if __name__ == "__main__":
	asyncio.run(main())
