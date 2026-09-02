package com.juren233.easyopen.shared.resources

import easyopen.shared.generated.resources.Res
import easyopen.shared.generated.resources.add_another_opener_section
import easyopen.shared.generated.resources.add_opener_dialog_summary
import easyopen.shared.generated.resources.add_opener_title
import easyopen.shared.generated.resources.allow_camera
import easyopen.shared.generated.resources.app_name
import easyopen.shared.generated.resources.auto_connect_opener
import easyopen.shared.generated.resources.auto_connect_range
import easyopen.shared.generated.resources.auto_connect_range_custom
import easyopen.shared.generated.resources.auto_connect_range_far
import easyopen.shared.generated.resources.auto_connect_range_moderate
import easyopen.shared.generated.resources.auto_connect_range_near
import easyopen.shared.generated.resources.auto_connect_signal_value
import easyopen.shared.generated.resources.auto_connect_signal_value_format
import easyopen.shared.generated.resources.auto_connect_signal_value_input
import easyopen.shared.generated.resources.auto_connect_signal_value_invalid
import easyopen.shared.generated.resources.auto_unlock_on_app_open
import easyopen.shared.generated.resources.automation_category
import easyopen.shared.generated.resources.back
import easyopen.shared.generated.resources.backup_failed
import easyopen.shared.generated.resources.backup_found
import easyopen.shared.generated.resources.backup_success
import easyopen.shared.generated.resources.backup_title
import easyopen.shared.generated.resources.battery_100
import easyopen.shared.generated.resources.battery_25
import easyopen.shared.generated.resources.battery_50
import easyopen.shared.generated.resources.battery_75
import easyopen.shared.generated.resources.battery_low
import easyopen.shared.generated.resources.battery_unknown
import easyopen.shared.generated.resources.bluetooth_permission_summary
import easyopen.shared.generated.resources.bluetooth_permission_title
import easyopen.shared.generated.resources.camera_permission_required
import easyopen.shared.generated.resources.camera_start_failed
import easyopen.shared.generated.resources.cancel
import easyopen.shared.generated.resources.close
import easyopen.shared.generated.resources.close_duration
import easyopen.shared.generated.resources.complete_pairing
import easyopen.shared.generated.resources.configure_opener_title
import easyopen.shared.generated.resources.continue_action
import easyopen.shared.generated.resources.current_device_summary
import easyopen.shared.generated.resources.data_category
import easyopen.shared.generated.resources.default_opener_advertised_name
import easyopen.shared.generated.resources.device_signal_summary
import easyopen.shared.generated.resources.device_summary
import easyopen.shared.generated.resources.discard_settings_message
import easyopen.shared.generated.resources.discard_settings_title
import easyopen.shared.generated.resources.error_address_invalid
import easyopen.shared.generated.resources.error_bluetooth_disabled
import easyopen.shared.generated.resources.error_bluetooth_permission
import easyopen.shared.generated.resources.error_connection_lost
import easyopen.shared.generated.resources.error_connection_timeout
import easyopen.shared.generated.resources.error_device_not_found
import easyopen.shared.generated.resources.error_invalid_password_parameter
import easyopen.shared.generated.resources.error_notifications_failed
import easyopen.shared.generated.resources.error_opener_response
import easyopen.shared.generated.resources.error_pairing_connection_lost
import easyopen.shared.generated.resources.error_pairing_connection_timeout
import easyopen.shared.generated.resources.error_pairing_notification_busy
import easyopen.shared.generated.resources.error_pairing_notifications_failed
import easyopen.shared.generated.resources.error_pairing_password_wrong
import easyopen.shared.generated.resources.error_pairing_service_missing
import easyopen.shared.generated.resources.error_pairing_service_not_ready
import easyopen.shared.generated.resources.error_pairing_services_failed
import easyopen.shared.generated.resources.error_pairing_timeout
import easyopen.shared.generated.resources.error_pairing_unknown_response
import easyopen.shared.generated.resources.error_pairing_write_busy
import easyopen.shared.generated.resources.error_pairing_write_exception
import easyopen.shared.generated.resources.error_pairing_write_status
import easyopen.shared.generated.resources.error_password_length
import easyopen.shared.generated.resources.error_password_not_configured
import easyopen.shared.generated.resources.error_scan_failed
import easyopen.shared.generated.resources.error_scan_start_failed
import easyopen.shared.generated.resources.error_scanner_unavailable
import easyopen.shared.generated.resources.error_service_missing
import easyopen.shared.generated.resources.error_services_failed
import easyopen.shared.generated.resources.error_unknown
import easyopen.shared.generated.resources.error_unlock_connection_lost
import easyopen.shared.generated.resources.error_unlock_notification_busy
import easyopen.shared.generated.resources.error_unlock_parameter_invalid
import easyopen.shared.generated.resources.error_unlock_service_not_ready
import easyopen.shared.generated.resources.error_unlock_timeout
import easyopen.shared.generated.resources.error_unlock_write_busy
import easyopen.shared.generated.resources.error_unlock_write_exception
import easyopen.shared.generated.resources.error_unlock_write_status
import easyopen.shared.generated.resources.first_use
import easyopen.shared.generated.resources.forward
import easyopen.shared.generated.resources.found_openers
import easyopen.shared.generated.resources.generate_share_qr
import easyopen.shared.generated.resources.grant_permission
import easyopen.shared.generated.resources.hold_duration
import easyopen.shared.generated.resources.home_title
import easyopen.shared.generated.resources.import_opener
import easyopen.shared.generated.resources.imported_opener_profile
import easyopen.shared.generated.resources.keep_opener_powered_nearby
import easyopen.shared.generated.resources.lock_direction
import easyopen.shared.generated.resources.monet_color_title
import easyopen.shared.generated.resources.nfc_dispatch_permission
import easyopen.shared.generated.resources.nfc_not_supported
import easyopen.shared.generated.resources.nfc_read_title
import easyopen.shared.generated.resources.nfc_turn_on
import easyopen.shared.generated.resources.nfc_write_choice_description
import easyopen.shared.generated.resources.nfc_write_choice_empty
import easyopen.shared.generated.resources.nfc_write_choice_title
import easyopen.shared.generated.resources.nfc_write_failed
import easyopen.shared.generated.resources.nfc_write_failed_unknown
import easyopen.shared.generated.resources.nfc_write_in_progress_description
import easyopen.shared.generated.resources.nfc_write_in_progress_title
import easyopen.shared.generated.resources.nfc_write_preserve_original
import easyopen.shared.generated.resources.nfc_write_reconnect_description
import easyopen.shared.generated.resources.nfc_write_reconnect_title
import easyopen.shared.generated.resources.nfc_write_success
import easyopen.shared.generated.resources.nfc_write_title
import easyopen.shared.generated.resources.nfc_write_waiting_description
import easyopen.shared.generated.resources.nfc_write_waiting_title
import easyopen.shared.generated.resources.nfc_write_without_original
import easyopen.shared.generated.resources.no_opener_found
import easyopen.shared.generated.resources.no_opener_found_summary
import easyopen.shared.generated.resources.one_tap_unlock
import easyopen.shared.generated.resources.open_bluetooth_settings
import easyopen.shared.generated.resources.open_duration
import easyopen.shared.generated.resources.opener_name
import easyopen.shared.generated.resources.opener_name_optional
import easyopen.shared.generated.resources.opener_settings_collapsed
import easyopen.shared.generated.resources.opener_settings_expanded
import easyopen.shared.generated.resources.pair_opener_section
import easyopen.shared.generated.resources.pairing_flow_page_transition
import easyopen.shared.generated.resources.pairing_password_in_progress
import easyopen.shared.generated.resources.password_dialog_description
import easyopen.shared.generated.resources.password_dialog_title
import easyopen.shared.generated.resources.password_field_label
import easyopen.shared.generated.resources.password_field_settings
import easyopen.shared.generated.resources.personalization_category
import easyopen.shared.generated.resources.qr_import_found
import easyopen.shared.generated.resources.qr_import_invalid
import easyopen.shared.generated.resources.rescan_backup_file
import easyopen.shared.generated.resources.rescan_from_gallery
import easyopen.shared.generated.resources.rescan_qr
import easyopen.shared.generated.resources.restore_backup
import easyopen.shared.generated.resources.restore_backup_file
import easyopen.shared.generated.resources.restore_failed
import easyopen.shared.generated.resources.restore_success
import easyopen.shared.generated.resources.restore_title
import easyopen.shared.generated.resources.retry
import easyopen.shared.generated.resources.reverse
import easyopen.shared.generated.resources.save_settings
import easyopen.shared.generated.resources.saved_opener_summary
import easyopen.shared.generated.resources.saved_openers
import easyopen.shared.generated.resources.scan_from_gallery
import easyopen.shared.generated.resources.scan_import_decoding
import easyopen.shared.generated.resources.scan_import_title
import easyopen.shared.generated.resources.search_again
import easyopen.shared.generated.resources.search_nearby_openers
import easyopen.shared.generated.resources.search_results
import easyopen.shared.generated.resources.searching
import easyopen.shared.generated.resources.select_all
import easyopen.shared.generated.resources.settings_title
import easyopen.shared.generated.resources.share_opener_title
import easyopen.shared.generated.resources.share_qr_content_description
import easyopen.shared.generated.resources.share_qr_summary
import easyopen.shared.generated.resources.share_qr_title
import easyopen.shared.generated.resources.start_search
import easyopen.shared.generated.resources.status_connected
import easyopen.shared.generated.resources.status_connecting
import easyopen.shared.generated.resources.status_disconnected
import easyopen.shared.generated.resources.status_discovered
import easyopen.shared.generated.resources.status_not_found
import easyopen.shared.generated.resources.switch_opener
import easyopen.shared.generated.resources.switch_opener_dialog_title
import easyopen.shared.generated.resources.theme_color_title
import easyopen.shared.generated.resources.theme_dark
import easyopen.shared.generated.resources.theme_light
import easyopen.shared.generated.resources.theme_system
import easyopen.shared.generated.resources.unlock_success
import easyopen.shared.generated.resources.update_available_notice
import easyopen.shared.generated.resources.verify_and_pair
import easyopen.shared.generated.resources.verifying

import org.jetbrains.compose.resources.StringResource

/** Public resource facade for the Android host and other platform modules. */
object EasyOpenStrings {
    val add_another_opener_section: StringResource
        get() = Res.string.add_another_opener_section
    val add_opener_dialog_summary: StringResource
        get() = Res.string.add_opener_dialog_summary
    val add_opener_title: StringResource
        get() = Res.string.add_opener_title
    val allow_camera: StringResource
        get() = Res.string.allow_camera
    val app_name: StringResource
        get() = Res.string.app_name
    val auto_connect_opener: StringResource
        get() = Res.string.auto_connect_opener
    val auto_connect_range: StringResource
        get() = Res.string.auto_connect_range
    val auto_connect_range_custom: StringResource
        get() = Res.string.auto_connect_range_custom
    val auto_connect_range_far: StringResource
        get() = Res.string.auto_connect_range_far
    val auto_connect_range_moderate: StringResource
        get() = Res.string.auto_connect_range_moderate
    val auto_connect_range_near: StringResource
        get() = Res.string.auto_connect_range_near
    val auto_connect_signal_value: StringResource
        get() = Res.string.auto_connect_signal_value
    val auto_connect_signal_value_format: StringResource
        get() = Res.string.auto_connect_signal_value_format
    val auto_connect_signal_value_input: StringResource
        get() = Res.string.auto_connect_signal_value_input
    val auto_connect_signal_value_invalid: StringResource
        get() = Res.string.auto_connect_signal_value_invalid
    val auto_unlock_on_app_open: StringResource
        get() = Res.string.auto_unlock_on_app_open
    val automation_category: StringResource
        get() = Res.string.automation_category
    val back: StringResource
        get() = Res.string.back
    val backup_failed: StringResource
        get() = Res.string.backup_failed
    val backup_found: StringResource
        get() = Res.string.backup_found
    val backup_success: StringResource
        get() = Res.string.backup_success
    val backup_title: StringResource
        get() = Res.string.backup_title
    val battery_100: StringResource
        get() = Res.string.battery_100
    val battery_25: StringResource
        get() = Res.string.battery_25
    val battery_50: StringResource
        get() = Res.string.battery_50
    val battery_75: StringResource
        get() = Res.string.battery_75
    val battery_low: StringResource
        get() = Res.string.battery_low
    val battery_unknown: StringResource
        get() = Res.string.battery_unknown
    val bluetooth_permission_summary: StringResource
        get() = Res.string.bluetooth_permission_summary
    val bluetooth_permission_title: StringResource
        get() = Res.string.bluetooth_permission_title
    val camera_permission_required: StringResource
        get() = Res.string.camera_permission_required
    val camera_start_failed: StringResource
        get() = Res.string.camera_start_failed
    val cancel: StringResource
        get() = Res.string.cancel
    val close: StringResource
        get() = Res.string.close
    val close_duration: StringResource
        get() = Res.string.close_duration
    val complete_pairing: StringResource
        get() = Res.string.complete_pairing
    val configure_opener_title: StringResource
        get() = Res.string.configure_opener_title
    val continue_action: StringResource
        get() = Res.string.continue_action
    val current_device_summary: StringResource
        get() = Res.string.current_device_summary
    val data_category: StringResource
        get() = Res.string.data_category
    val default_opener_advertised_name: StringResource
        get() = Res.string.default_opener_advertised_name
    val device_signal_summary: StringResource
        get() = Res.string.device_signal_summary
    val device_summary: StringResource
        get() = Res.string.device_summary
    val discard_settings_message: StringResource
        get() = Res.string.discard_settings_message
    val discard_settings_title: StringResource
        get() = Res.string.discard_settings_title
    val error_address_invalid: StringResource
        get() = Res.string.error_address_invalid
    val error_bluetooth_disabled: StringResource
        get() = Res.string.error_bluetooth_disabled
    val error_bluetooth_permission: StringResource
        get() = Res.string.error_bluetooth_permission
    val error_connection_lost: StringResource
        get() = Res.string.error_connection_lost
    val error_connection_timeout: StringResource
        get() = Res.string.error_connection_timeout
    val error_device_not_found: StringResource
        get() = Res.string.error_device_not_found
    val error_invalid_password_parameter: StringResource
        get() = Res.string.error_invalid_password_parameter
    val error_notifications_failed: StringResource
        get() = Res.string.error_notifications_failed
    val error_opener_response: StringResource
        get() = Res.string.error_opener_response
    val error_pairing_connection_lost: StringResource
        get() = Res.string.error_pairing_connection_lost
    val error_pairing_connection_timeout: StringResource
        get() = Res.string.error_pairing_connection_timeout
    val error_pairing_notification_busy: StringResource
        get() = Res.string.error_pairing_notification_busy
    val error_pairing_notifications_failed: StringResource
        get() = Res.string.error_pairing_notifications_failed
    val error_pairing_password_wrong: StringResource
        get() = Res.string.error_pairing_password_wrong
    val error_pairing_service_missing: StringResource
        get() = Res.string.error_pairing_service_missing
    val error_pairing_service_not_ready: StringResource
        get() = Res.string.error_pairing_service_not_ready
    val error_pairing_services_failed: StringResource
        get() = Res.string.error_pairing_services_failed
    val error_pairing_timeout: StringResource
        get() = Res.string.error_pairing_timeout
    val error_pairing_unknown_response: StringResource
        get() = Res.string.error_pairing_unknown_response
    val error_pairing_write_busy: StringResource
        get() = Res.string.error_pairing_write_busy
    val error_pairing_write_exception: StringResource
        get() = Res.string.error_pairing_write_exception
    val error_pairing_write_status: StringResource
        get() = Res.string.error_pairing_write_status
    val error_password_length: StringResource
        get() = Res.string.error_password_length
    val error_password_not_configured: StringResource
        get() = Res.string.error_password_not_configured
    val error_scan_failed: StringResource
        get() = Res.string.error_scan_failed
    val error_scan_start_failed: StringResource
        get() = Res.string.error_scan_start_failed
    val error_scanner_unavailable: StringResource
        get() = Res.string.error_scanner_unavailable
    val error_service_missing: StringResource
        get() = Res.string.error_service_missing
    val error_services_failed: StringResource
        get() = Res.string.error_services_failed
    val error_unknown: StringResource
        get() = Res.string.error_unknown
    val error_unlock_connection_lost: StringResource
        get() = Res.string.error_unlock_connection_lost
    val error_unlock_notification_busy: StringResource
        get() = Res.string.error_unlock_notification_busy
    val error_unlock_parameter_invalid: StringResource
        get() = Res.string.error_unlock_parameter_invalid
    val error_unlock_service_not_ready: StringResource
        get() = Res.string.error_unlock_service_not_ready
    val error_unlock_timeout: StringResource
        get() = Res.string.error_unlock_timeout
    val error_unlock_write_busy: StringResource
        get() = Res.string.error_unlock_write_busy
    val error_unlock_write_exception: StringResource
        get() = Res.string.error_unlock_write_exception
    val error_unlock_write_status: StringResource
        get() = Res.string.error_unlock_write_status
    val first_use: StringResource
        get() = Res.string.first_use
    val forward: StringResource
        get() = Res.string.forward
    val found_openers: StringResource
        get() = Res.string.found_openers
    val generate_share_qr: StringResource
        get() = Res.string.generate_share_qr
    val grant_permission: StringResource
        get() = Res.string.grant_permission
    val hold_duration: StringResource
        get() = Res.string.hold_duration
    val home_title: StringResource
        get() = Res.string.home_title
    val import_opener: StringResource
        get() = Res.string.import_opener
    val imported_opener_profile: StringResource
        get() = Res.string.imported_opener_profile
    val keep_opener_powered_nearby: StringResource
        get() = Res.string.keep_opener_powered_nearby
    val lock_direction: StringResource
        get() = Res.string.lock_direction
    val monet_color_title: StringResource
        get() = Res.string.monet_color_title
    val nfc_dispatch_permission: StringResource
        get() = Res.string.nfc_dispatch_permission
    val nfc_not_supported: StringResource
        get() = Res.string.nfc_not_supported
    val nfc_read_title: StringResource
        get() = Res.string.nfc_read_title
    val nfc_turn_on: StringResource
        get() = Res.string.nfc_turn_on
    val nfc_write_choice_description: StringResource
        get() = Res.string.nfc_write_choice_description
    val nfc_write_choice_empty: StringResource
        get() = Res.string.nfc_write_choice_empty
    val nfc_write_choice_title: StringResource
        get() = Res.string.nfc_write_choice_title
    val nfc_write_failed: StringResource
        get() = Res.string.nfc_write_failed
    val nfc_write_failed_unknown: StringResource
        get() = Res.string.nfc_write_failed_unknown
    val nfc_write_in_progress_description: StringResource
        get() = Res.string.nfc_write_in_progress_description
    val nfc_write_in_progress_title: StringResource
        get() = Res.string.nfc_write_in_progress_title
    val nfc_write_preserve_original: StringResource
        get() = Res.string.nfc_write_preserve_original
    val nfc_write_reconnect_description: StringResource
        get() = Res.string.nfc_write_reconnect_description
    val nfc_write_reconnect_title: StringResource
        get() = Res.string.nfc_write_reconnect_title
    val nfc_write_success: StringResource
        get() = Res.string.nfc_write_success
    val nfc_write_title: StringResource
        get() = Res.string.nfc_write_title
    val nfc_write_waiting_description: StringResource
        get() = Res.string.nfc_write_waiting_description
    val nfc_write_waiting_title: StringResource
        get() = Res.string.nfc_write_waiting_title
    val nfc_write_without_original: StringResource
        get() = Res.string.nfc_write_without_original
    val no_opener_found: StringResource
        get() = Res.string.no_opener_found
    val no_opener_found_summary: StringResource
        get() = Res.string.no_opener_found_summary
    val one_tap_unlock: StringResource
        get() = Res.string.one_tap_unlock
    val open_bluetooth_settings: StringResource
        get() = Res.string.open_bluetooth_settings
    val open_duration: StringResource
        get() = Res.string.open_duration
    val opener_name: StringResource
        get() = Res.string.opener_name
    val opener_name_optional: StringResource
        get() = Res.string.opener_name_optional
    val opener_settings_collapsed: StringResource
        get() = Res.string.opener_settings_collapsed
    val opener_settings_expanded: StringResource
        get() = Res.string.opener_settings_expanded
    val pair_opener_section: StringResource
        get() = Res.string.pair_opener_section
    val pairing_flow_page_transition: StringResource
        get() = Res.string.pairing_flow_page_transition
    val pairing_password_in_progress: StringResource
        get() = Res.string.pairing_password_in_progress
    val password_dialog_description: StringResource
        get() = Res.string.password_dialog_description
    val password_dialog_title: StringResource
        get() = Res.string.password_dialog_title
    val password_field_label: StringResource
        get() = Res.string.password_field_label
    val password_field_settings: StringResource
        get() = Res.string.password_field_settings
    val personalization_category: StringResource
        get() = Res.string.personalization_category
    val qr_import_found: StringResource
        get() = Res.string.qr_import_found
    val qr_import_invalid: StringResource
        get() = Res.string.qr_import_invalid
    val rescan_backup_file: StringResource
        get() = Res.string.rescan_backup_file
    val rescan_from_gallery: StringResource
        get() = Res.string.rescan_from_gallery
    val rescan_qr: StringResource
        get() = Res.string.rescan_qr
    val restore_backup: StringResource
        get() = Res.string.restore_backup
    val restore_backup_file: StringResource
        get() = Res.string.restore_backup_file
    val restore_failed: StringResource
        get() = Res.string.restore_failed
    val restore_success: StringResource
        get() = Res.string.restore_success
    val restore_title: StringResource
        get() = Res.string.restore_title
    val retry: StringResource
        get() = Res.string.retry
    val reverse: StringResource
        get() = Res.string.reverse
    val save_settings: StringResource
        get() = Res.string.save_settings
    val saved_opener_summary: StringResource
        get() = Res.string.saved_opener_summary
    val saved_openers: StringResource
        get() = Res.string.saved_openers
    val scan_from_gallery: StringResource
        get() = Res.string.scan_from_gallery
    val scan_import_decoding: StringResource
        get() = Res.string.scan_import_decoding
    val scan_import_title: StringResource
        get() = Res.string.scan_import_title
    val search_again: StringResource
        get() = Res.string.search_again
    val search_nearby_openers: StringResource
        get() = Res.string.search_nearby_openers
    val search_results: StringResource
        get() = Res.string.search_results
    val searching: StringResource
        get() = Res.string.searching
    val select_all: StringResource
        get() = Res.string.select_all
    val settings_title: StringResource
        get() = Res.string.settings_title
    val share_opener_title: StringResource
        get() = Res.string.share_opener_title
    val share_qr_content_description: StringResource
        get() = Res.string.share_qr_content_description
    val share_qr_summary: StringResource
        get() = Res.string.share_qr_summary
    val share_qr_title: StringResource
        get() = Res.string.share_qr_title
    val start_search: StringResource
        get() = Res.string.start_search
    val status_connected: StringResource
        get() = Res.string.status_connected
    val status_connecting: StringResource
        get() = Res.string.status_connecting
    val status_disconnected: StringResource
        get() = Res.string.status_disconnected
    val status_discovered: StringResource
        get() = Res.string.status_discovered
    val status_not_found: StringResource
        get() = Res.string.status_not_found
    val switch_opener: StringResource
        get() = Res.string.switch_opener
    val switch_opener_dialog_title: StringResource
        get() = Res.string.switch_opener_dialog_title
    val theme_color_title: StringResource
        get() = Res.string.theme_color_title
    val theme_dark: StringResource
        get() = Res.string.theme_dark
    val theme_light: StringResource
        get() = Res.string.theme_light
    val theme_system: StringResource
        get() = Res.string.theme_system
    val unlock_success: StringResource
        get() = Res.string.unlock_success
    val update_available_notice: StringResource
        get() = Res.string.update_available_notice
    val verify_and_pair: StringResource
        get() = Res.string.verify_and_pair
    val verifying: StringResource
        get() = Res.string.verifying
}
