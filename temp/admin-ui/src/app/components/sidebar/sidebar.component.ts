import { Component, OnDestroy, OnInit, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { WindowSizeService } from '../../services/window-size-service';

declare const $: any;

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.less']
})
export class SidebarComponent implements OnInit, OnDestroy, AfterViewInit {

  isMobileDevice = window.screen.width <= 900;
  private resizeSubscription: Subscription;

  constructor(private sizeService: WindowSizeService) {
  }
  ngAfterViewInit(): void {
    if($('.sidebar-submenu.collapse')){
      $('.sidebar-submenu.collapse').collapse('hide');
    }
  }

  ngOnInit() {
    this.resizeSubscription = this.sizeService.onResize$.subscribe(mobileDevice => this.isMobileDevice = mobileDevice);
  }

  ngOnDestroy() {
    if (this.resizeSubscription) {
      this.resizeSubscription.unsubscribe();
    }
  }
}
