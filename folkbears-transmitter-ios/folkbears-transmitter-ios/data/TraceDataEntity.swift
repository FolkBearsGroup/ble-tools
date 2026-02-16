import UIKit
import CoreData

@objc(TraceData)
final class TraceDataEntity: NSManagedObject, Encodable {
}

extension TraceDataEntity {
    @NSManaged var tempId: String?
    @NSManaged var timestamp: Date?
    @NSManaged var rssi: NSNumber?
    @NSManaged var txPower: NSNumber?

    func set(traceData: TraceData) {
        setValue(traceData.timestamp, forKeyPath: "timestamp")
        setValue(traceData.tempId, forKey: "tempId")
        setValue(traceData.rssi, forKeyPath: "rssi")
        setValue(traceData.txPower, forKeyPath: "txPower")
    }

    func toTraceData() -> TraceData {
        return TraceData(timestamp: timestamp, tempId: tempId, rssi: rssi?.doubleValue, txPower: txPower?.doubleValue)
    }
}

struct TraceData  {
    var timestamp: Date?
    var tempId: String?
    var rssi: Double?
    var txPower: Double?
}
