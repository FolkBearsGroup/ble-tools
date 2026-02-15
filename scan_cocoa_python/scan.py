"""
BLE Exposure Notification scanner in Python.
Rewrites the Windows C# watcher into a cross-platform script using bleak.

install:
    pip install bleak
run:
    python scan.py
"""

import asyncio
import datetime as _dt
from typing import Dict

from bleak import BleakScanner


TARGET_UUID = "0000fd6f-0000-1000-8000-00805f9b34fb"


def _format_mac(address: str) -> str:
	parts = address.split(":")
	if len(parts) == 6:
		return ":".join(parts[2:]).upper()
	return address.upper()


def _extract_rpi(service_data: Dict[str, bytes]) -> bytes | None:
	for uuid, data in service_data.items():
		if uuid.lower() != TARGET_UUID:
			continue
		if len(data) >= 16:
			# Some platforms strip the service UUID from service data; others keep it.
			return data[-16:]
	return None


def _now_str() -> str:
	return _dt.datetime.now().strftime("%Y/%m/%d %H:%M:%S.%f")[:23]


def _detection_callback(device, advertisement_data) -> None:
	uuids = [u.lower() for u in advertisement_data.service_uuids or []]
	if TARGET_UUID not in uuids:
		return

	rpi = _extract_rpi(advertisement_data.service_data or {})
	if rpi is None:
		return

	time = _now_str()
	rssi = advertisement_data.rssi
	mac = _format_mac(device.address or "")
	print(f"{time} [{rpi.hex()}] {rssi} dBm {mac}")


async def main() -> None:
	print("Folkbears ProveCheck")
	scanner = BleakScanner(detection_callback=_detection_callback)
	await scanner.start()
	print("Press Enter to continue")
	try:
		await asyncio.get_event_loop().run_in_executor(None, input)
	finally:
		await scanner.stop()


if __name__ == "__main__":
	asyncio.run(main())
