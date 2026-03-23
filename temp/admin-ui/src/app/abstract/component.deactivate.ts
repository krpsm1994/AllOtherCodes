/**
 * https://
 * medium.com/better-programming/angular-how-keep-user-from-lost-his-data-by-accidentally-leaving-the-page-before-submit-4eeb74420f0d
 */

import { HostListener, Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root'
  })
export abstract class ComponentCanDeactivate {
    // handle the navigation change event
    abstract canDeactivate(): boolean;

    // handle the tab closed/refreshed event
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = true;
        }
    }
}
