import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from '@/routes/ProtectedRoute';
import DashboardLayout from '@/components/layout/DashboardLayout';
import LoginPage from '@/pages/auth/LoginPage';
import DashboardPage from '@/pages/dashboard/DashboardPage';
import OrganizationsListPage from '@/pages/organizations/OrganizationsListPage';
import OrganizationFormPage from '@/pages/organizations/OrganizationFormPage';
import ProjectsListPage from '@/pages/projects/ProjectsListPage';
import ProjectFormPage from '@/pages/projects/ProjectFormPage';
import EnvironmentsListPage from '@/pages/environments/EnvironmentsListPage';
import EnvironmentFormPage from '@/pages/environments/EnvironmentFormPage';
import FeatureFlagsListPage from '@/pages/flags/FeatureFlagsListPage';
import FeatureFlagFormPage from '@/pages/flags/FeatureFlagFormPage';
import SegmentsListPage from '@/pages/segments/SegmentsListPage';
import SegmentFormPage from '@/pages/segments/SegmentFormPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<DashboardLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />

          <Route path="/organizations" element={<OrganizationsListPage />} />
          <Route path="/organizations/new" element={<OrganizationFormPage />} />
          <Route path="/organizations/:id/edit" element={<OrganizationFormPage />} />

          <Route path="/projects" element={<ProjectsListPage />} />
          <Route path="/projects/new" element={<ProjectFormPage />} />
          <Route path="/projects/:id/edit" element={<ProjectFormPage />} />

          <Route path="/environments" element={<EnvironmentsListPage />} />
          <Route path="/environments/new" element={<EnvironmentFormPage />} />
          <Route path="/environments/:id/edit" element={<EnvironmentFormPage />} />

          <Route path="/flags" element={<FeatureFlagsListPage />} />
          <Route path="/flags/new" element={<FeatureFlagFormPage />} />
          <Route path="/flags/:id/edit" element={<FeatureFlagFormPage />} />

          <Route path="/segments" element={<SegmentsListPage />} />
          <Route path="/segments/new" element={<SegmentFormPage />} />
          <Route path="/segments/:id/edit" element={<SegmentFormPage />} />

          {/*
            API Keys and Audit Logs routes will be added here
            one by one in upcoming steps.
          */}
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
