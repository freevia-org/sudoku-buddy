# App privacy draft

Prepared 19 October 2026 from the iOS code and existing service policy. Account holder must reconcile these answers with the deployed service before submission. Do not select “Data Not Collected”: optional ongoing reporting is disclosed conservatively.

| Data type | Purpose | Linked to identity | Tracking |
| --- | --- | --- | --- |
| User Content > Photos or Videos | App Functionality; Other Purposes (recognition improvement/private training) | No | No |
| User Content > Gameplay Content | App Functionality; Other Purposes | No | No |
| User Content > Customer Support | App Functionality; Other Purposes | No for in-app anonymous reports | No |
| Diagnostics > Other Diagnostic Data | App Functionality; Other Purposes | No for in-app anonymous reports | No |

The four rows match `PrivacyInfo.xcprivacy`. Reports contain a cropped puzzle photo, app version, grids, cell classes/confidences, geometry, uncertain markers, correction timestamps and an opaque per-puzzle/revision identifier. No device/ad/user identifier is sent by the report archive. The revision identifier serves retry/deletion, not cross-app tracking. No advertising, tracking permission, location collection or routine analytics SDK exists.

Photos, gameplay and diagnostics processed only on the device are not sent unless the user chooses a report/share action or opts into future uncertain-reading submission. Requests pass through Cloudflare; existing policy describes infrastructure IP processing and private report retention. The account holder must verify the infrastructure does not retain additional data that changes these answers.

Email and GitHub are external services. An email sender or GitHub account can identify the author. Review actual support retention and Apple's optional-disclosure rules; add Contact Info or identity linkage if those support practices require it. Do not treat anonymous in-app reports as proof that every external support interaction is anonymous.

Publishing gate: reconcile the cross-platform source in `../privacy-policy.md` with the live https://freevia.org/sudoku-buddy/privacy page. It responded successfully on 19 October, but the revised source has not been published. Include policy link in App Store Connect and keep the in-app link.

Sources: [Apple app privacy details](https://developer.apple.com/app-store/app-privacy-details/), [manage privacy](https://developer.apple.com/help/app-store-connect/manage-app-information/manage-app-privacy/).
