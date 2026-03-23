import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { MonitorMessagesComponent } from './monitor-messages.component';

describe('MonitorMessagesComponent', () => {
  let component: MonitorMessagesComponent;
  let fixture: ComponentFixture<MonitorMessagesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [MonitorMessagesComponent]
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MonitorMessagesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
