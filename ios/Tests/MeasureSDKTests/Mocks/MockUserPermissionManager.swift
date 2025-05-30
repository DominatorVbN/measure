//
//  MockUserPermissionManager.swift
//  MeasureSDKTests
//
//  Created by Adwin Ross on 04/02/25.
//

import Foundation
@testable import Measure

final class MockUserPermissionManager: UserPermissionManager {
    var isPermissionGranted = true

    func isPhotoLibraryUsagePermissionAvailable() -> Bool {
        return isPermissionGranted
    }
}
