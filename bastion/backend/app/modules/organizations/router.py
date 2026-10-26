from fastapi import APIRouter

from app.core.deps import DB, SuperAdmin
from app.modules.organizations import service
from app.modules.organizations.schemas import OrganizationCreate, OrganizationOut, OrganizationUpdate

router = APIRouter(prefix="/organizations", tags=["organizations"])


@router.get("", response_model=list[OrganizationOut])
def list_organizations(_: SuperAdmin, db: DB) -> list[OrganizationOut]:
    return [OrganizationOut.model_validate(o) for o in service.list_all(db)]


@router.post("", response_model=OrganizationOut, status_code=201)
def create_organization(body: OrganizationCreate, _: SuperAdmin, db: DB) -> OrganizationOut:
    return OrganizationOut.model_validate(service.create(db, body))


@router.patch("/{org_id}", response_model=OrganizationOut)
def update_organization(org_id: int, body: OrganizationUpdate, _: SuperAdmin, db: DB) -> OrganizationOut:
    return OrganizationOut.model_validate(service.update_org(db, org_id, body.name, body.active))
