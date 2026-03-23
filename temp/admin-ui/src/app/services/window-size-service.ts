/**
 * Logic from here: https://stackoverflow.com/a/43833815
 */
import { Injectable, Renderer2, RendererFactory2 } from '@angular/core';
import { Subject, Observable } from 'rxjs';

declare const $: any;

@Injectable({
    providedIn: 'root'
})
export class WindowSizeService {

    resizeSubject: Subject<boolean>;
    private isMobile: boolean;
    private renderer: Renderer2;

    constructor(private rendererFactory: RendererFactory2) {
        this.resizeSubject = new Subject();
        this.renderer = this.rendererFactory.createRenderer(null, null);
        this.renderer.listen('window', 'resize', this.onResize.bind(this));
    }

    get onResize$(): Observable<boolean> {
        return this.resizeSubject.asObservable();
    }

    private onResize(event: any) {
        this.isMobile = event.target.innerWidth <= 900;
        this.resizeSubject.next(this.isMobile);
    }
}
