import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { MetadataSchemaComponent } from './metadata-schema.component';

describe('MetadataSchemaComponent', () => {
  let component: MetadataSchemaComponent;
  let fixture: ComponentFixture<MetadataSchemaComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [MetadataSchemaComponent]
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MetadataSchemaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
